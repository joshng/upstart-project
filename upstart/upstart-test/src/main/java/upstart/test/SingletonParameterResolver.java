package upstart.test;

import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;

public interface SingletonParameterResolver<T> extends SingletonExtension<T>, ParameterResolver {
  @Override
  default boolean supportsParameter(
          ParameterContext parameterContext,
          ExtensionContext extensionContext
  ) throws ParameterResolutionException {
    return parameterContext.getParameter().getType() == contextClass();
  }

  @Override
  default Object resolveParameter(
          ParameterContext parameterContext,
          ExtensionContext extensionContext
  ) throws ParameterResolutionException {
    return getOrCreateContext(extensionContext);
  }
}
