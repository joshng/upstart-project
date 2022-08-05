package upstart.util.functions;

import upstart.util.concurrent.CompletableFutures;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

@FunctionalInterface
public interface AsyncFunction<I, O> extends Function<I, CompletionStage<O>> {
  static <I, O> AsyncFunction<I, O> asyncFunction(AsyncFunction<I, O> fn) {
    return fn;
  }

  default CompletableFuture<O> callSafely(I input) {
    return CompletableFutures.callSafely(() -> apply(input));
  }

  default AsyncFunction<I, O> withSafeWrapper() {
    return new Safe<>(this);
  }

  class Safe<I, O> implements AsyncFunction<I, O> {
    private final AsyncFunction<I, O> unsafe;

    public Safe(AsyncFunction<I, O> unsafe) {
      this.unsafe = unsafe;
    }

    @Override
    public CompletableFuture<O> apply(I i) {
      return unsafe.callSafely(i);
    }

    @Override
    public AsyncFunction<I, O> withSafeWrapper() {
      return this;
    }

    @Override
    public CompletableFuture<O> callSafely(I input) {
      return apply(input);
    }
  }
}
