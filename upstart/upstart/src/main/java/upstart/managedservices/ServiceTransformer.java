package upstart.managedservices;

import com.google.common.util.concurrent.Service;
import upstart.UpstartService;
import upstart.util.concurrent.CompletableFutures;
import upstart.util.concurrent.services.ComposableService;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Supplier;

import static com.google.common.base.Strings.lenientFormat;

public class ServiceTransformer<S extends Service, T> implements Supplier<T> {
  private final S service;
  private final CompletableFuture<T> transformedValue;

  protected ServiceTransformer(S service, Supplier<? extends T> valueSupplier) {
    this.service = service;
    transformedValue = ComposableService.enhance(service).getStartedFuture()
            .thenApply(ignored -> valueSupplier.get());
  }

  @Override
  public T get() {
    if (!service.isRunning()) {
      throw new IllegalStateException(lenientFormat("Service was not running: %s", service, UpstartService.latestInjector().getInstance(ManagedServicesModule.INFRASTRUCTURE_GRAPH_KEY)));
    }
    return transformedValue.join();
  }

  protected S service() {
    return service;
  }

  public <V> Supplier<V> whenRunning(Function<? super T, V> function) {
    CompletableFuture<V> future = transformedValue.thenApply(function);
    return () -> CompletableFutures.getDone(future);
  }
}
