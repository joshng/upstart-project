package upstart.util.concurrent;


import upstart.util.exceptions.ThrowingConsumer;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Supplier;

/**
 * A simple extension of {@link CompletableFuture} with utility methods for completing a "Promise" in various ways.
 */
public class Promise<T> extends AbstractPromise<T, Promise<T>> {
  /**
   * Encapsulates the common pattern of allocating a Future, performing some initialization to prepare for its
   * asynchronous completion, and then returning that Future. For example, the following common pattern:
   * <pre>{@code
   * CompletableFuture<X> future = new CompletableFuture<X>();
   * remoteService.submitWithCallback(args, (result, error) -> {
   *   if (error == null) {
   *     future.complete(result));
   *   } else {
   *       future.completeExceptionally(error);
   *   }
   * });
   * return future;
   * }</pre>
   * <p>
   * ... can be more concise:
   *
   * <pre>{@code
   * return Promise.thatCompletes(promise -> remoteService.submitWithCallback(args, promise::accept));
   * }</pre>
   */
  public static <T> Promise<T> thatCompletes(ThrowingConsumer<? super Promise<T>> initializer) {
    Promise<T> promise = new Promise<>();
    return promise.consumeFailure(() -> initializer.accept(promise));
  }

  public static <T> Promise<T> of(CompletionStage<T> stage) {
    return (stage instanceof Promise promise) ? promise : new Promise<T>().completeWith(stage);
  }

  public static <T> Promise<T> completed(T result) {
    return new Promise<T>().fulfill(result);
  }

  public static <T> Promise<T> completeAsync(Callable<? extends CompletionStage<? extends T>> completionSupplier) {
    return completeAsync(completionSupplier, ForkJoinPool.commonPool());
  }

  public static <T> Promise<T> completeAsync(Callable<? extends CompletionStage<? extends T>> completionSupplier, Executor executor) {
    return Promise.thatCompletes(promise -> CompletableFuture.runAsync(() -> promise.tryCompleteWith(completionSupplier), executor));
  }

  public static <T> Promise<T> callAsync(Callable<? extends T> callable, Executor executor) {
    return completeAsync(() -> CompletableFuture.completedFuture(callable.call()), executor);
  }

  @Override
  protected PromiseFactory<Promise<T>> factory() {
    return Promise::new;
  }

  @Override
  protected Promise<T> withSideEffect(Supplier<CompletableFuture<T>> superCall) {
    return (Promise<T>) superCall.get();
  }
}
