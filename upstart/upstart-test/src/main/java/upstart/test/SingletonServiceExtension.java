package upstart.test;

import com.google.common.util.concurrent.Service;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import upstart.config.EnvironmentConfigFixture;

import java.time.Duration;
import java.util.Optional;

public interface SingletonServiceExtension<T extends Service> extends SingletonExtension<T>, BeforeEachCallback, AfterEachCallback {
  default Optional<Duration> getStartupTimeout(ExtensionContext context) {
    return Optional.empty();
  }

  default Optional<Duration> getShutdownTimout(ExtensionContext context) {
    return Optional.empty();
  }

  T createService(ExtensionContext context) throws Exception;

  @Override
  default T createContext(ExtensionContext extensionContext) throws Exception {
    T service = createService(extensionContext);
    Optional<Duration> startupTimeout = getStartupTimeout(extensionContext);
    if (startupTimeout.isPresent()) {
      service.startAsync().awaitRunning(startupTimeout.get());
    } else {
      service.startAsync().awaitRunning();
    }
    return service;
  }

  @Override
  default void beforeEach(ExtensionContext context) throws Exception {
    getOrCreateContext(context);
  }

  @Override
  default void afterEach(ExtensionContext context) throws Exception {
    Optional<T> existingContext = getExistingContext(context);
    if (existingContext.isPresent()) {
      T service = existingContext.get();
      Optional<Duration> shutdownTimeout = getShutdownTimout(context);
      if (shutdownTimeout.isPresent()) {
        service.stopAsync().awaitTerminated(shutdownTimeout.get());
      } else {
        service.stopAsync().awaitTerminated();
      }
    }
  }
}
