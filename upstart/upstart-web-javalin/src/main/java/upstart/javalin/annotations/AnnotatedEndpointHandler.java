package upstart.javalin.annotations;

import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import io.javalin.Javalin;
import io.javalin.core.security.RouteRole;
import io.javalin.http.ContentType;
import io.javalin.http.Context;
import io.javalin.http.Handler;
import io.javalin.http.HandlerType;
import io.javalin.plugin.openapi.annotations.AnnotationApiMappingKt;
import io.javalin.plugin.openapi.annotations.OpenApi;
import io.javalin.plugin.openapi.dsl.OpenApiBuilder;
import io.javalin.plugin.openapi.dsl.OpenApiDocumentation;
import io.javalin.plugin.openapi.dsl.OpenApiUpdater;
import org.mockito.internal.util.Primitives;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import upstart.proxy.Proxies;
import upstart.util.reflect.Modifiers;
import upstart.util.collect.PairStream;
import upstart.util.reflect.Reflect;
import upstart.util.concurrent.LazyReference;
import upstart.util.concurrent.ThreadLocalReference;
import upstart.util.strings.MoreStrings;

import java.io.InputStream;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

public class AnnotatedEndpointHandler<T> {
  private static final Logger LOG = LoggerFactory.getLogger(AnnotatedEndpointHandler.class);
  private final Map<Method, Endpoint> endpoints;
  private final Class<T> type;
  private final LazyReference<RouteProxyInterceptor> routeProxy = LazyReference.from(RouteProxyInterceptor::new);
  private final RouteRole[] classRoles;

  AnnotatedEndpointHandler(Class<T> type, HttpRegistry registry) {
    this.type = type;
    classRoles = registry.getRequiredRoles(type);

    endpoints = PairStream.withMappedValues(
            Reflect.allAnnotatedMethods(
                    type,
                    Http.class,
                    Reflect.LineageOrder.SuperclassBeforeSubclass
            ), method -> {
              Http annotation = method.getAnnotation(Http.class);
              return new Endpoint(
                      method,
                      annotation.verb().handlerType,
                      annotation.path(),
                      annotation.successStatusCode(),
                      methodRoles(registry.getRequiredRoles(method))
              );
            }).toImmutableMap();
    checkArgument(!endpoints.isEmpty(), "No @Http endpoint-methods found in class: %s", type);
  }

  private RouteRole[] methodRoles(RouteRole[] declaredRoles) {
    return classRoles.length == 0
            ? declaredRoles
            : declaredRoles.length == 0
            ? classRoles
            :
            Stream.concat(
                    Stream.of(declaredRoles),
                    Stream.of(classRoles)
            ).distinct()
                    .toArray(RouteRole[]::new);
  }

  public void installHandlers(T target, Javalin javalin) {
    for (Endpoint endpoint : endpoints.values()) {
      endpoint.register(javalin, target);
    }
  }

  public HttpUrl urlFor(Consumer<? super T> methodInvoker) {
    return routeProxy.get().capture(methodInvoker);
  }

  private static class Endpoint {
    private static final String OK_STATUS = "200";
    private final Method method;
    private final HandlerType handlerType;
    private final String path;
    private final RouteRole[] requiredRoles;
    private final List<ParamResolver> paramResolvers;
    private final BiConsumer<Context, Object> resultDispatcher;
    private final OpenApiDocumentation documentation;
    private boolean mappedBody = false;

    private Endpoint(
            Method method,
            HandlerType handlerType,
            String path,
            int successStatusCode,
            RouteRole[] requiredRoles
    ) {
      checkArgument(
              Modifiers.Public.matches(method),
              "@Http method %s.%s must be public",
              method.getDeclaringClass().getSimpleName(),
              method.getName()
      );
      this.method = method;
      paramResolvers = Arrays.stream(method.getParameters())
              .map(this::buildResolver)
              .collect(ImmutableList.toImmutableList());
      this.handlerType = handlerType;
      this.path = path;
      this.requiredRoles = requiredRoles;
      documentation = buildDocumentation();
      String successStatus = Integer.toString(successStatusCode);
      BiConsumer<Context, ?> responder;
      Class<?> returnType = method.getReturnType();
      if (returnType == void.class) {
        responder = ((c, o) -> { });
      } else if (InputStream.class.isAssignableFrom(returnType)) {
        // TODO: is this correct?
        documentation.result(successStatus, byte[].class, ContentType.OCTET_STREAM);
        responder = (BiConsumer<Context, InputStream>) Context::result;
      } else if (CompletionStage.class.isAssignableFrom(returnType)) {
        // TODO: deal with further generics, arrays, etc
        Class<?> futureType = Reflect.getFirstGenericType(method.getGenericReturnType());
        documentation.result(successStatus, futureType, ContentType.JSON);
        responder = (Context context, CompletionStage<?> o) -> context.future(o.toCompletableFuture());
      } else {
        documentation.result(successStatus, returnType, ContentType.JSON);
        responder = Context::json;
      }

      //noinspection unchecked
      resultDispatcher = (BiConsumer<Context, Object>) responder;
    }

    private OpenApiDocumentation buildDocumentation() {
      OpenApiDocumentation documentation = Optional.ofNullable(method.getAnnotation(OpenApi.class))
              .map(AnnotationApiMappingKt::asOpenApiDocumentation)
              .orElseGet(OpenApiBuilder::document);

      for (ParamResolver resolver : paramResolvers) {
        resolver.applyUpdates(documentation);
      }

      return documentation;
    }

    public void register(Javalin javalin, Object target) {
      LOG.info("Registered route {}[{}] => {}.{}(...)", handlerType, path, method.getDeclaringClass().getSimpleName(), method.getName());
      Handler handler = OpenApiBuilder.documented(documentation, (Handler) ctx -> invoke(target, ctx));
      javalin.addHandler(handlerType, path, handler, requiredRoles);
    }

    void invoke(Object target, Context ctx) throws Exception {
      var args = new Object[paramResolvers.size()];
      for (int i = 0; i < paramResolvers.size(); i++) {
        args[i] = paramResolvers.get(i).resolve(ctx);
      }

      try {
        Object result = method.invoke(target, args);
        resultDispatcher.accept(ctx, result);
      } catch (InvocationTargetException e) {
        Throwable cause = e.getCause();
        Throwables.throwIfInstanceOf(cause, Exception.class);
        throw e;
      }
    }

    public HttpUrl buildUrl(Object... args) {
      assert args.length == paramResolvers.size();
      var builder = new UrlBuilder(path);
      for (int i = 0; i < args.length; i++) {
        paramResolvers.get(i).applyToUrl(builder, args[i]);
      }
      return builder.build();
    }

    private ParamResolver buildResolver(Parameter parameter) {
      Class<?> paramType = parameter.getType();
      if (paramType == Context.class) {
        return ParamResolver.nonUrlParam(parameter, Optional.empty(), ctx -> ctx);
      } else if (parameter.isAnnotationPresent(PathParam.class)) {
        return pathParamResolver(parameter);
      } else if (parameter.isAnnotationPresent(QueryParam.class)) {
        return queryParamResolver(parameter);
      } else if (parameter.isAnnotationPresent(Session.class)) {
        String name = paramName(parameter.getAnnotation(Session.class).value(), parameter);
        return ParamResolver.nonUrlParam(parameter, Optional.empty(), ctx -> ctx.sessionAttribute(name));
      } else {
        checkArgument(!mappedBody, "Method has multiple unannotated parameters", method);
        mappedBody = true;
        if (paramType == String.class) {
          return ParamResolver.nonUrlParam(parameter, Optional.of(paramType), Context::body);
        } else if (paramType == byte[].class) {
          return ParamResolver.nonUrlParam(parameter, Optional.of(paramType), Context::bodyAsBytes);
        } else if (paramType == InputStream.class) {
          return ParamResolver.nonUrlParam(parameter, Optional.of(byte[].class), Context::bodyAsInputStream);
        } else {
          return ParamResolver.nonUrlParam(parameter, Optional.of(paramType), ctx -> ctx.bodyAsClass(paramType));
        }
      }
    }

    private ParamResolver pathParamResolver(Parameter parameter) {
      Class<?> paramType = parameter.getType();
      String name = paramName(parameter.getAnnotation(PathParam.class).value(), parameter);
      return ParamResolver.of(
              name,
              ParamResolver.UrlParamType.Path,
              Optional.of(paramType),
              paramType == String.class
                      ? ctx -> ctx.pathParam(name)
                      : ctx -> ctx.pathParamAsClass(name, paramType).get()
      );
    }

    private ParamResolver queryParamResolver(Parameter parameter) {
      Class<?> paramType = parameter.getType();
      String name = paramName(parameter.getAnnotation(QueryParam.class).value(), parameter);
      return ParamResolver.of(
              name,
              ParamResolver.UrlParamType.Query,
              Optional.of(paramType),
              paramType == String.class
                      ? ctx -> ctx.queryParam(name)
                      : ctx -> ctx.queryParamAsClass(name, paramType).get()
      );
    }

    private String paramName(String annotatedName, Parameter param) {
      if (annotatedName.isEmpty()) {
        String name = param.getName();
        checkState(param.isNamePresent(), "Parameter-name unavailable for %s parameter %s (method %s)", param.getType().getSimpleName(), name, method);
        return MoreStrings.toLowerSnakeCase(name);
      } else {
        return annotatedName;
      }
    }

    private static class ParamResolver implements OpenApiUpdater<OpenApiDocumentation> {
      private static final OpenApiUpdater<OpenApiDocumentation> NO_DOCUMENTATION = ignored -> {};

      private final String paramName;
      private final UrlParamType paramType;
      private final OpenApiUpdater<OpenApiDocumentation> documentation;
      private final Function<Context, Object> resolver;

      ParamResolver(
              String paramName,
              UrlParamType paramType,
              Optional<Class<?>> documentedType,
              Function<Context, Object> resolver
      ) {
        this.paramName = paramName;
        this.paramType = paramType;
        this.documentation = documentedType
                .<OpenApiUpdater<OpenApiDocumentation>>map(type -> paramType == UrlParamType.None
                        ? (doc -> doc.body(type))
                        : NO_DOCUMENTATION)
                .orElse(NO_DOCUMENTATION);
        this.resolver = resolver;
      }

      public static ParamResolver nonUrlParam(Parameter parameter, Optional<Class<?>> documentedType, Function<Context, Object> resolver) {
        return of(parameter.getName(), UrlParamType.None, documentedType, resolver);
      }

      public static ParamResolver of(String name, UrlParamType type, Optional<Class<?>> documentedType, Function<Context, Object> resolver) {
        return new ParamResolver(name, type, documentedType, resolver);
      }

      public Object resolve(Context context) {
        return resolver.apply(context);
      }

      public void applyToUrl(UrlBuilder urlBuilder, Object value) {
        paramType.applyToUrl(urlBuilder, paramName, value);
      }

      @Override
      public void applyUpdates(OpenApiDocumentation documentation) {
        this.documentation.applyUpdates(documentation);
      }

      public enum UrlParamType {
        Path,
        Query,
        None;

        public void applyToUrl(UrlBuilder urlBuilder, String paramName, Object value) {
          switch (this) {
            case Path -> urlBuilder.withPathParam(paramName, value);
            case Query -> urlBuilder.withQueryParam(paramName, value);
            case None -> checkArgument(
                    value == null || value.equals(Primitives.defaultValue(value.getClass())),
                    "Non-null/default value passed to HttpRoutes proxy-method for parameter '%s'",
                    paramName
            );
          }
        }
      }
    }
  }

  class RouteProxyInterceptor implements InvocationHandler {
    final T proxy = Proxies.createProxy(type, this);
    final ThreadLocalReference<HttpUrl> requestedRoute = new ThreadLocalReference<>();


    public HttpUrl capture(Consumer<? super T> invoker) {
      try {
        invoker.accept(proxy);
        return checkNotNull(requestedRoute.get(), "No proxy-method was invoked");
      } finally {
        requestedRoute.remove();
      }
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
      HttpUrl path = endpoints.get(method).buildUrl(args);
      checkState(requestedRoute.getAndSet(path) == null);
      return null;
    }
  }
}
