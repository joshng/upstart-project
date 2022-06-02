package upstart.util.concurrent;

import upstart.util.SelfType;
import upstart.util.exceptions.ThrowingRunnable;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

public abstract class ExtendedPromise<T, P extends ExtendedPromise<T, P>> extends Promise<T> implements SelfType<P> {

  protected abstract Promise.PromiseFactory<P> factory();

  public P tryComplete(Callable<T> completion) {
    consumeFailure(() -> complete(completion.call()));
    return self();
  }

  public P tryComplete(T value, ThrowingRunnable completion) {
    return consumeFailure(() -> {
      completion.runOrThrow();
      complete(value);
    });
  }

  public P consumeFailure(ThrowingRunnable runnable) {
    if (!isDone()) {
      try {
        runnable.runOrThrow();
      } catch (Throwable e) {
        completeExceptionally(e);
      }
    }
    return self();
  }

  /**
   * Complete this Future with the result of the Future returned by the given {@link Callable} (or any exception that it throws).
   */
  public P tryCompleteWith(Callable<? extends CompletionStage<? extends T>> completion) {
    return consumeFailure(() -> completeWith(completion.call().toCompletableFuture()));
  }

  public P fulfill(T result) {
    complete(result);
    return self();
  }

  /**
   * Complete this Future with the same result as the given {@link CompletionStage completion}.
   *
   * @return self() Promise (which could potentially still be completed via other means)
   */
  public P completeWith(CompletionStage<? extends T> completion) {
    completion.whenComplete(this);
    return self();
  }

  /**
   * If the given {@link CompletionStage completion} completes exceptionally, then complete this Promise with the same
   * exception. Otherwise, this Promise is unaffected.
   *
   * @return this Promise
   */
  public P failWith(CompletionStage<? extends T> completion) {
    completion.whenComplete((ignored, e) -> {
      if (e != null) completeExceptionally(e);
    });
    return self();
  }

  /**
   * Arranges to run the given {@link Runnable} when this Promise is completed (regardless of whether the completion
   * is normal or exceptional).<p/>
   * <p>
   * Note that this uses {@link #whenComplete} to invoke the sideEffect, which implies that the returned {@link Promise}
   * will not reflect any exception that may be thrown by the sideEffect;
   *
   * @return a Promise which completes after this Promise is done, and the sideEffect has executed.
   */
  public P uponCompletion(Runnable sideEffect) {
    return whenComplete((t, e) -> sideEffect.run());
  }

  public <E extends Throwable> P recover(Class<E> exceptionType, Function<? super E, ? extends T> recovery) {
    return (P) CompletableFutures.recover(this, exceptionType, recovery);
  }

  public P onCancel(Runnable callback) {
    return withSideEffect(() -> CompletableFutures.whenCancelled(this, callback));
  }

  @Override
  public P whenComplete(BiConsumer<? super T, ? super Throwable> action) {
    return withSideEffect(() -> super.whenComplete(action));
  }

  @Override
  public P whenCompleteAsync(BiConsumer<? super T, ? super Throwable> action) {
    return withSideEffect(() -> super.whenCompleteAsync(action));
  }

  @Override
  public P whenCompleteAsync(BiConsumer<? super T, ? super Throwable> action, Executor executor) {
    return withSideEffect(() -> super.whenCompleteAsync(action, executor));
  }

  @Override
  public P toCompletableFuture() {
    return self();
  }

  @Override
  public P exceptionally(Function<Throwable, ? extends T> fn) {
    return withSideEffect(() -> super.exceptionally(fn));
  }

  @Override
  public P orTimeout(long timeout, TimeUnit unit) {
    super.orTimeout(timeout, unit);
    return self();
  }

  protected P withSideEffect(Supplier<CompletableFuture<T>> superCall) {
    return chainWithFactory(superCall, factory());
  }
}
