package upstart.util.functions;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Supplier;

@FunctionalInterface
public interface AsyncSupplier<T> extends Supplier<CompletableFuture<T>>, Function<Object, CompletableFuture<T>> {

  static <T> AsyncSupplier<T> asyncSupplier(AsyncSupplier<T> supplier) {
    return supplier;
  }

  @Override
  default CompletableFuture<T> apply(Object o) {
    return get();
  }
}
