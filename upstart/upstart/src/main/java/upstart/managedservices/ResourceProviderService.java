package upstart.managedservices;

import com.google.common.util.concurrent.Service;

import javax.inject.Provider;

import static com.google.common.base.Preconditions.checkState;

public interface ResourceProviderService<T> extends Provider<T>, Service {
  T getResource();

  default T get() {
    checkState(isRunning(), "Service is not running");
    return getResource();
  }
}
