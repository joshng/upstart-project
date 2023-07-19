package upstart.test;

import com.google.common.base.Throwables;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.reflect.TypeToken;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import upstart.config.EnvironmentConfigFixture;
import upstart.util.exceptions.Exceptions;
import upstart.util.reflect.Reflect;

import java.lang.reflect.Type;
import java.util.Optional;
import java.util.stream.Stream;

public interface SingletonExtension<T> {
  Type CONTEXT_TYPE = SingletonExtension.class.getTypeParameters()[0];
  LoadingCache<Class<?>, TypeToken<?>> PARAMETER_TYPES = CacheBuilder.newBuilder().build(
          CacheLoader.from(
                  extensionClass -> TypeToken.of(extensionClass).resolveType(CONTEXT_TYPE))
  );

  static <T> T getOrCreateContextFrom(
          Class<? extends SingletonExtension<T>> extensionClass,
          ExtensionContext extensionContext
  ) {
    return Exceptions.callUnchecked(extensionClass::newInstance).getOrCreateContext(extensionContext);
  }

  static <T> Optional<T> getExistingContextFrom(
          Class<? extends SingletonExtension<T>> extensionClass,
          ExtensionContext extensionContext
  ) {
    return Exceptions.callUnchecked(extensionClass::newInstance).getExistingContext(extensionContext);
  }

  static <T> T getRequiredContextFrom(
          Class<? extends SingletonExtension<T>> extensionClass,
          ExtensionContext extensionContext
  ) {
    return getExistingContextFrom(extensionClass, extensionContext).orElseThrow(() -> new IllegalStateException(
            "Test must be extended with " + extensionClass.getSimpleName()));
  }

  static <T> Optional<? extends T> getOrCreateOptionalContext(
          Class<T> contextClass,
          ExtensionContext extensionContext
  ) {
    return ExtensionContexts.findRepeatableTestAnnotations(
                    ExtendWith.class,
                    Reflect.LineageOrder.SubclassBeforeSuperclass,
                    extensionContext
            )
            .map(ExtendWith::value)
            .flatMap(Stream::of)
            .filter(SingletonExtension.class::isAssignableFrom)
            .filter(extClass -> PARAMETER_TYPES.getUnchecked(extClass).isSubtypeOf(contextClass))
            .map(Reflect::<Class<? extends SingletonExtension<T>>>blindCast)
            .findFirst()
            .map(extensionClass -> getOrCreateContextFrom(extensionClass, extensionContext));
  }

  T createContext(ExtensionContext extensionContext) throws Exception;

  Class<T> contextClass();

  default ExtensionContext.Namespace getNamespace() {
    return ExtensionContext.Namespace.GLOBAL;
  }

  default T getOrCreateContext(ExtensionContext extensionContext) {
    return extensionContext.getStore(getNamespace()).getOrComputeIfAbsent(contextClass(), __ -> {
      try {
        T context = createContext(extensionContext);
        if (context instanceof EnvironmentConfigFixture configFixture) {
          UpstartExtension.applyOptionalEnvironmentValues(extensionContext, configFixture);
        }
        return context;
      } catch (Exception e) {
        Throwables.throwIfUnchecked(e);
        throw new RuntimeException(e);
      }
    }, contextClass());
  }

  default Optional<T> getExistingContext(ExtensionContext extensionContext) {
    return Optional.ofNullable(extensionContext.getStore(getNamespace()).get(contextClass(), contextClass()));
  }
}
