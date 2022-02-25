package upstart.javalin.annotations;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.PrivateModule;
import com.google.inject.TypeLiteral;
import io.javalin.core.JavalinConfig;
import io.javalin.core.security.RouteRole;
import upstart.config.UpstartModule;
import upstart.guice.PrivateBinding;
import upstart.guice.TypeLiterals;
import upstart.javalin.JavalinWebInitializer;
import upstart.javalin.JavalinWebModule;
import upstart.util.Reflect;
import upstart.util.Validation;

import javax.inject.Inject;
import java.io.Serializable;
import java.lang.annotation.Annotation;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.invoke.SerializedLambda;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkState;

public class HttpRegistry<A extends Annotation> {
  private final LoadingCache<Class<?>, AnnotatedEndpointHandler<?>> handlerCache = CacheBuilder.newBuilder()
          .build(new CacheLoader<>() {
            @SuppressWarnings("unchecked")
            @Override
            public AnnotatedEndpointHandler<?> load(Class<?> key) throws Exception {
              return new AnnotatedEndpointHandler<>((Class<Object>) key, HttpRegistry.this);
            }
          });

  private final Class<A> roleAnnotationClass;
  private final Function<A, RouteRole[]> getRoles;

  public HttpRegistry(Class<A> roleAnnotationClass, Function<A, RouteRole[]> getRoles) {
    Retention retention = roleAnnotationClass.getAnnotation(Retention.class);
    Validation.success()
            .confirm(retention != null && retention.value() == RetentionPolicy.RUNTIME,
                     "@Retention(RUNTIME)"
            )
            .confirm(
                    roleAnnotationClass.isAnnotationPresent(Http.AccessControlAnnotation.class),
                    "@%s.%s",
                    Http.class.getSimpleName(),
                    Http.AccessControlAnnotation.class.getSimpleName()
            ).throwFailures(messages -> new IllegalArgumentException(messages.stream().collect(Collectors.joining(
                    "\n  ",
                    "@interface " + roleAnnotationClass.getName() + " must be meta-annotated with:\n  ",
                    ""
            ))));

    this.roleAnnotationClass = roleAnnotationClass;
    this.getRoles = getRoles;
  }

  RouteRole[] getRequiredRoles(Method method) {
    RouteRole[] required = Reflect.findMetaAnnotations(roleAnnotationClass, method)
            .map(getRoles)
            .flatMap(Arrays::stream)
            .distinct()
            .toArray(RouteRole[]::new);
    if (required.length == 0) {
      Set<Annotation> otherAccessControl = Reflect.allMetaAnnotations(method)
              .filter(anno -> anno.annotationType().getAnnotation(Http.AccessControlAnnotation.class) != null)
              .collect(Collectors.toSet());
      checkState(
              otherAccessControl.isEmpty(),
              "Found mismatched access-control annotation(s) %s on method: %s",
              otherAccessControl,
              method
      );
    }
    return required;
  }

  public <T> HttpRoutes<T> getRoutes(HttpUrl rootPath, Class<T> controllerClass) {
    return new HttpRoutes<T>(rootPath, handlerFor(controllerClass));
  }

  @SuppressWarnings("unchecked")
  private <T> AnnotatedEndpointHandler<T> handlerFor(Class<T> targetClass) {
    return (AnnotatedEndpointHandler<T>) handlerCache.getUnchecked(targetClass);
  }

  public Module webEndpointModule(Key<?> endpointPojoKey) {
    return new AnnotatedEndpointModule<>(endpointPojoKey);
  }

  private class AnnotatedEndpointModule<T> extends UpstartModule implements JavalinWebModule {
    private final Key<T> targetKey;

    private AnnotatedEndpointModule(
            Key<T> targetKey
    ) {
      this.targetKey = targetKey;
    }

    @Override
    protected void configure() {
      Class<T> targetType = TypeLiterals.getRawType(targetKey.getTypeLiteral());
      AnnotatedEndpointHandler<T> handler = handlerFor(targetType);
      TypeLiteral<AnnotatedWebInitializer<T>> initializerType = TypeLiterals.getParameterizedWithOwner(
              HttpRegistry.class,
              AnnotatedWebInitializer.class,
              targetType
      );
      install(new PrivateModule() {
        @Override
        protected void configure() {
          bind(HttpRegistry.class).toInstance(HttpRegistry.this);
          bind(targetKey.withAnnotation(PrivateBinding.class)).to(targetKey);
          bind(initializerType);
          expose(initializerType);
        }
      });
      Key<AnnotatedEndpointHandler<T>> handlerKey = Key.get(TypeLiterals.getParameterized(AnnotatedEndpointHandler.class, targetType));
      if (targetKey.getAnnotation() != null) handlerKey = handlerKey.withAnnotation(targetKey.getAnnotation());
      bind(handlerKey).toInstance(handler);
      addJavalinWebBinding().to(initializerType);
    }
  }

  private static class AnnotatedWebInitializer<T> implements JavalinWebInitializer {
    private final T target;
    private final AnnotatedEndpointHandler<T> handler;

    @Inject
    public AnnotatedWebInitializer(@PrivateBinding T target, AnnotatedEndpointHandler<T> handler) {
      this.target = target;
      this.handler = handler;
    }

    @Override
    public void initializeWeb(JavalinConfig config) {
      config.registerPlugin(javalin -> handler.installHandlers(target, javalin));
      if (target instanceof JavalinWebInitializer initializer) {
        initializer.initializeWeb(config);
      }
    }
  }
}
