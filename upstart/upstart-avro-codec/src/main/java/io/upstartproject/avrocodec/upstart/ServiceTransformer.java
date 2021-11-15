package io.upstartproject.avrocodec.upstart;

import com.google.common.util.concurrent.Service;
import upstart.services.ComposableService;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import static com.google.common.base.Preconditions.checkState;

public class ServiceTransformer<T> implements Supplier<T> {
  private final Service service;
  private final CompletableFuture<T> transformedValue;

  protected ServiceTransformer(Service service, Supplier<? extends T> valueSupplier) {
    this.service = service;
    transformedValue = ComposableService.enhance(service).getStartedFuture()
            .thenApply(ignored -> valueSupplier.get());
  }

  @Override
  public T get() {
    checkState(service.isRunning(), "Service was not running: %s", service);
    return transformedValue.join();
  }
}
