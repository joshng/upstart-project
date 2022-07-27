package upstart.javalin.annotations;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.PrivateModule;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.Multibinder;
import io.javalin.Javalin;
import io.javalin.core.JavalinConfig;
import io.javalin.core.security.RouteRole;
import upstart.config.UpstartModule;
import upstart.guice.PrivateBinding;
import upstart.guice.TypeLiterals;
import upstart.javalin.JavalinWebInitializer;
import upstart.javalin.JavalinWebModule;
import upstart.util.reflect.Reflect;
import upstart.util.Validation;

import javax.inject.Inject;
import java.lang.annotation.Annotation;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkNotNull;

public class HttpRegistry {
  public static final HttpRegistry INSTANCE = new HttpRegistry();

  private final LoadingCache<Class<?>, AnnotatedEndpointHandler<?>> handlerCache = CacheBuilder.newBuilder()
          .build(new CacheLoader<>() {
            @Override
            public AnnotatedEndpointHandler<?> load(Class<?> key) throws Exception {
              return new AnnotatedEndpointHandler<>(Reflect.blindCast(key), HttpRegistry.this);
            }
          });

  private final Map<Class<? extends Annotation>, Function<Annotation, SecurityConstraints>> constraintReaders = new HashMap<>();

  public <A extends Annotation> void registerRequiredRoleAnnotation(Class<A> roleAnnotationClass, Function<A, RouteRole[]> getRoles) {
    registerSecurityAnnotation(
            roleAnnotationClass,
            anno -> SecurityConstraints.builder()
                    .addRequiredRoles(getRoles.apply(anno))
                    .build()
    );
  }

  public <A extends Annotation> void registerSecurityAnnotation(
          Class<A> annotationClass,
          Function<A, SecurityConstraints> getConstraints
  ) {
    Retention retention = annotationClass.getAnnotation(Retention.class);
    Validation.success()
            .confirm(retention != null && retention.value() == RetentionPolicy.RUNTIME,
                     "@Retention(RUNTIME)"
            )
            .confirm(
                    annotationClass.isAnnotationPresent(Http.AccessControlAnnotation.class),
                    "@%s.%s",
                    Http.class.getSimpleName(),
                    Http.AccessControlAnnotation.class.getSimpleName()
            ).throwFailures(messages -> new IllegalArgumentException(messages.stream().collect(Collectors.joining(
                    "\n  ",
                    "@interface " + annotationClass.getName() + " must be meta-annotated with:\n  ",
                    ""
            ))));

    constraintReaders.put(annotationClass, Reflect.blindCast(getConstraints));
  }

  SecurityConstraints getSecurityConstraints(AnnotatedElement element) {
    return Reflect.allMetaAnnotations(element)
            .filter(anno -> anno.annotationType().isAnnotationPresent(Http.AccessControlAnnotation.class))
            .map(anno -> securityReader(element, anno).apply(anno))
                   .reduce(SecurityConstraints.NONE, SecurityConstraints::merge);
  }

  private Function<Annotation, SecurityConstraints> securityReader(AnnotatedElement element, Annotation anno) {
    return checkNotNull(
            constraintReaders.get(anno.annotationType()),
            "Http.AccessControlAnnotation must be registered with HttpRegistry.registerAccessControlAnnotation: @%s %s",
            anno,
            element
    );
  }

  public <T> HttpRoutes<T> getRoutes(HttpUrl rootPath, Class<T> controllerClass) {
    return new HttpRoutes<>(rootPath, handlerFor(controllerClass));
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
      super(targetKey);
      this.targetKey = targetKey;
    }

    @Override
    protected void configure() {
      AnnotatedEndpointHandler<T> handler = handlerFor(TypeLiterals.getRawType(targetKey.getTypeLiteral()));
      Type targetType = targetKey.getTypeLiteral().getType();
      TypeLiteral<AnnotatedEndpointInitializer<T>> initializerType = TypeLiterals.getParameterizedWithOwner(
              HttpRegistry.class,
              AnnotatedEndpointInitializer.class,
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
      Key<AnnotatedEndpointHandler<T>> handlerKey = Key.get(
              TypeLiterals.getParameterized(AnnotatedEndpointHandler.class, targetType)
      );
      if (targetKey.getAnnotation() != null) handlerKey = handlerKey.withAnnotation(targetKey.getAnnotation());
      bind(handlerKey).toInstance(handler);
      Multibinder.<AnnotatedEndpointInitializer<?>>newSetBinder(binder(), new TypeLiteral<>(){}).addBinding().to(initializerType);
      install(new AnnotatedWebInitializer.Module());
    }
  }

  private static class AnnotatedWebInitializer implements JavalinWebInitializer {
    private final ObjectMapper objectMapper;
    private final Set<AnnotatedEndpointInitializer<?>> endpointInitializers;

    @Inject
    AnnotatedWebInitializer(@Web ObjectMapper objectMapper, Set<AnnotatedEndpointInitializer<?>> endpointInitializers) {
      this.objectMapper = objectMapper;
      this.endpointInitializers = endpointInitializers;
    }

    @Override
    public void initializeWeb(JavalinConfig config) {
      for (AnnotatedEndpointInitializer<?> endpointInitializer : endpointInitializers) {
        endpointInitializer.initializeWeb(config);
      }
      config.registerPlugin(javalin -> {
        for (AnnotatedEndpointInitializer<?> endpointInitializer : endpointInitializers) {
          endpointInitializer.installHandlers(javalin);
        }
        javalin.attribute(AnnotatedEndpointHandler.OBJECT_MAPPER_ATTRIBUTE, objectMapper);
      });
    }

    static class Module extends UpstartModule implements JavalinWebModule {
      @Override
      protected void configure() {
        addJavalinWebBinding().to(AnnotatedWebInitializer.class);
      }
    }
  }

  private static class AnnotatedEndpointInitializer<T> implements JavalinWebInitializer {
    private final T target;
    private final AnnotatedEndpointHandler<T> handler;

    @Inject
    public AnnotatedEndpointInitializer(@PrivateBinding T target, AnnotatedEndpointHandler<T> handler) {
      this.target = target;
      this.handler = handler;
    }

    @Override
    public void initializeWeb(JavalinConfig config) {
      if (target instanceof JavalinWebInitializer initializer) {
        initializer.initializeWeb(config);
      }
    }

    void installHandlers(Javalin javalin) {
      handler.installHandlers(target, javalin);
    }
  }
}
