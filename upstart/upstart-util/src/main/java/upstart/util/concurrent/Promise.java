package upstart.util.concurrent;


import upstart.util.exceptions.ThrowingConsumer;
import upstart.util.exceptions.ThrowingRunnable;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * A simple extension of {@link CompletableFuture} with utility methods for completing a "Promise" in various ways.
 */
public class Promise<T> extends CompletableFuture<T> implements BiConsumer<T, Throwable> {
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
    return (stage instanceof Promise) ? (Promise<T>) stage : new Promise<T>().completeWith(stage);
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

  /**
   * Complete this Future with the result of calling the given {@link Callable}, or with any exception that it throws.
   */
  public Promise<T> tryComplete(Callable<T> completion) {
    consumeFailure(() -> complete(completion.call()));
    return this;
  }

  public Promise<T> tryComplete(T value, ThrowingRunnable completion) {
    return consumeFailure(() -> {
      completion.runOrThrow();
      complete(value);
    });
  }

  public Promise<T> consumeFailure(ThrowingRunnable runnable) {
    if (!isDone()) {
      try {
        runnable.runOrThrow();
      } catch (Throwable e) {
        completeExceptionally(e);
      }
    }
    return this;
  }

  /**
   * Complete this Future with the result of the Future returned by the given {@link Callable} (or any exception that it throws).
   */
  public Promise<T> tryCompleteWith(Callable<? extends CompletionStage<? extends T>> completion) {
    return consumeFailure(() -> completeWith(completion.call().toCompletableFuture()));
  }

  public Promise<T> fulfill(T result) {
    complete(result);
    return this;
  }

  /**
   * Complete this Future with the same result as the given {@link CompletionStage completion}.
   * @return this Promise (which could potentially still be completed via other means)
   */
  public Promise<T> completeWith(CompletionStage<? extends T> completion) {
    completion.whenComplete(this);
    return this;
  }

  /**
   * If the given {@link CompletionStage completion} completes exceptionally, then complete this Promise with the same
   * exception. Otherwise, this Promise is unaffected.
   * @return this Promise
   */
  public Promise<T> failWith(CompletionStage<? extends T> completion) {
    completion.whenComplete((ignored, e) -> {
      if (e != null) completeExceptionally(e);
    });
    return this;
  }

  /**
   * Arranges to run the given {@link Runnable} when this Promise is completed (regardless of whether the completion
   * is normal or exceptional).<p/>
   *
   * Note that this uses {@link #whenComplete} to invoke the sideEffect, which implies that the returned {@link Promise}
   * will not reflect any exception that may be thrown by the sideEffect;
   * @return a Promise which completes after this Promise is done, and the sideEffect has executed.
   */
  public Promise<T> uponCompletion(Runnable sideEffect) {
    return whenComplete((t, e) -> sideEffect.run());
  }

  public Promise<Void> toVoid() {
    return thenReplace(null);
  }
  public <U> Promise<U> thenReplace(U value) {
    return thenApply(__ -> value);
  }

  public <U> Promise<U> thenReplaceFrom(Supplier<U> supplier) {
    return thenApply(__ -> supplier.get());
  }

  public <U> CompletableFuture<U> thenReplaceFuture(Supplier<? extends CompletionStage<U>> supplier) {
    return thenCompose(__ -> supplier.get());
  }

  @SuppressWarnings("unchecked")
  public <U> Promise<U> thenApply(Function<? super T, ? extends U> fn) {
    return (Promise<U>) super.thenApply(fn);
  }

  public Promise<T> onCancel(Runnable callback) {
    return (Promise<T>) CompletableFutures.whenCancelled(this, callback);
  }

  @Override
  public void accept(T t, Throwable throwable) {
    if (throwable == null) {
      complete(t);
    } else {
      completeExceptionally(throwable);
    }
  }

  @Override
  public <U> Promise<U> newIncompleteFuture() {
    return new Promise<>();
  }

  @SuppressWarnings("unchecked")
  @Override
  public <U> Promise<U> thenApplyAsync(Function<? super T, ? extends U> fn) {
    return (Promise<U>) super.thenApplyAsync(fn);
  }

  @SuppressWarnings("unchecked")
  @Override
  public <U> Promise<U> thenApplyAsync(Function<? super T, ? extends U> fn, Executor executor) {
    return (Promise<U>) super.thenApplyAsync(fn, executor);
  }

  @Override
  public Promise<Void> thenAccept(Consumer<? super T> action) {
    return (Promise<Void>) super.thenAccept(action);
  }

  @Override
  public Promise<Void> thenAcceptAsync(Consumer<? super T> action) {
    return (Promise<Void>) super.thenAcceptAsync(action);
  }

  @Override
  public Promise<Void> thenAcceptAsync(Consumer<? super T> action, Executor executor) {
    return (Promise<Void>) super.thenAcceptAsync(action, executor);
  }

  @Override
  public Promise<Void> thenRun(Runnable action) {
    return (Promise<Void>) super.thenRun(action);
  }

  @Override
  public Promise<Void> thenRunAsync(Runnable action) {
    return (Promise<Void>) super.thenRunAsync(action);
  }

  @Override
  public Promise<Void> thenRunAsync(Runnable action, Executor executor) {
    return (Promise<Void>) super.thenRunAsync(action, executor);
  }

  @SuppressWarnings("unchecked")
  @Override
  public <U, V> Promise<V> thenCombine(CompletionStage<? extends U> other, BiFunction<? super T, ? super U, ? extends V> fn) {
    return (Promise<V>) super.thenCombine(other, fn);
  }

  @SuppressWarnings("unchecked")
  @Override
  public <U, V> Promise<V> thenCombineAsync(CompletionStage<? extends U> other, BiFunction<? super T, ? super U, ? extends V> fn) {
    return (Promise<V>) super.thenCombineAsync(other, fn);
  }

  @SuppressWarnings("unchecked")
  @Override
  public <U, V> Promise<V> thenCombineAsync(CompletionStage<? extends U> other, BiFunction<? super T, ? super U, ? extends V> fn, Executor executor) {
    return (Promise<V>) super.thenCombineAsync(other, fn, executor);
  }

  @Override
  public <U> Promise<Void> thenAcceptBoth(CompletionStage<? extends U> other, BiConsumer<? super T, ? super U> action) {
    return (Promise<Void>) super.thenAcceptBoth(other, action);
  }

  @Override
  public <U> Promise<Void> thenAcceptBothAsync(CompletionStage<? extends U> other, BiConsumer<? super T, ? super U> action) {
    return (Promise<Void>) super.thenAcceptBothAsync(other, action);
  }

  @Override
  public <U> Promise<Void> thenAcceptBothAsync(CompletionStage<? extends U> other, BiConsumer<? super T, ? super U> action, Executor executor) {
    return (Promise<Void>) super.thenAcceptBothAsync(other, action, executor);
  }

  @Override
  public Promise<Void> runAfterBoth(CompletionStage<?> other, Runnable action) {
    return (Promise<Void>) super.runAfterBoth(other, action);
  }

  @Override
  public Promise<Void> runAfterBothAsync(CompletionStage<?> other, Runnable action) {
    return (Promise<Void>) super.runAfterBothAsync(other, action);
  }

  @Override
  public Promise<Void> runAfterBothAsync(CompletionStage<?> other, Runnable action, Executor executor) {
    return (Promise<Void>) super.runAfterBothAsync(other, action, executor);
  }

  @Override
  public <U> Promise<U> applyToEither(CompletionStage<? extends T> other, Function<? super T, U> fn) {
    return (Promise<U>) super.applyToEither(other, fn);
  }

  @Override
  public <U> Promise<U> applyToEitherAsync(CompletionStage<? extends T> other, Function<? super T, U> fn) {
    return (Promise<U>) super.applyToEitherAsync(other, fn);
  }

  @Override
  public <U> Promise<U> applyToEitherAsync(CompletionStage<? extends T> other, Function<? super T, U> fn, Executor executor) {
    return (Promise<U>) super.applyToEitherAsync(other, fn, executor);
  }

  @Override
  public Promise<Void> acceptEither(CompletionStage<? extends T> other, Consumer<? super T> action) {
    return (Promise<Void>) super.acceptEither(other, action);
  }

  @Override
  public Promise<Void> acceptEitherAsync(CompletionStage<? extends T> other, Consumer<? super T> action) {
    return (Promise<Void>) super.acceptEitherAsync(other, action);
  }

  @Override
  public Promise<Void> acceptEitherAsync(CompletionStage<? extends T> other, Consumer<? super T> action, Executor executor) {
    return (Promise<Void>) super.acceptEitherAsync(other, action, executor);
  }

  @Override
  public Promise<Void> runAfterEither(CompletionStage<?> other, Runnable action) {
    return (Promise<Void>) super.runAfterEither(other, action);
  }

  @Override
  public Promise<Void> runAfterEitherAsync(CompletionStage<?> other, Runnable action) {
    return (Promise<Void>) super.runAfterEitherAsync(other, action);
  }

  @Override
  public Promise<Void> runAfterEitherAsync(CompletionStage<?> other, Runnable action, Executor executor) {
    return (Promise<Void>) super.runAfterEitherAsync(other, action, executor);
  }

  @Override
  public <U> Promise<U> thenCompose(Function<? super T, ? extends CompletionStage<U>> fn) {
    return (Promise<U>) super.thenCompose(fn);
  }

  @Override
  public <U> Promise<U> thenComposeAsync(Function<? super T, ? extends CompletionStage<U>> fn) {
    return (Promise<U>) super.thenComposeAsync(fn);
  }

  @Override
  public <U> Promise<U> thenComposeAsync(Function<? super T, ? extends CompletionStage<U>> fn, Executor executor) {
    return (Promise<U>) super.thenComposeAsync(fn, executor);
  }

  @Override
  public Promise<T> whenComplete(BiConsumer<? super T, ? super Throwable> action) {
    return (Promise<T>) super.whenComplete(action);
  }

  @Override
  public Promise<T> whenCompleteAsync(BiConsumer<? super T, ? super Throwable> action) {
    return (Promise<T>) super.whenCompleteAsync(action);
  }

  @Override
  public Promise<T> whenCompleteAsync(BiConsumer<? super T, ? super Throwable> action, Executor executor) {
    return (Promise<T>) super.whenCompleteAsync(action, executor);
  }

  @SuppressWarnings("unchecked")
  @Override
  public <U> Promise<U> handle(BiFunction<? super T, Throwable, ? extends U> fn) {
    return (Promise<U>) super.handle(fn);
  }

  @SuppressWarnings("unchecked")
  @Override
  public <U> Promise<U> handleAsync(BiFunction<? super T, Throwable, ? extends U> fn) {
    return (Promise<U>) super.handleAsync(fn);
  }

  @SuppressWarnings("unchecked")
  @Override
  public <U> Promise<U> handleAsync(BiFunction<? super T, Throwable, ? extends U> fn, Executor executor) {
    return (Promise<U>) super.handleAsync(fn, executor);
  }

  @Override
  public CompletableFuture<T> toCompletableFuture() {
    return this;
  }

  @Override
  public Promise<T> exceptionally(Function<Throwable, ? extends T> fn) {
    return (Promise<T>) super.exceptionally(fn);
  }

}
