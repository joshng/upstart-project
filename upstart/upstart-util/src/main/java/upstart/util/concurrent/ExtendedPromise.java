package upstart.util.concurrent;

import upstart.util.SelfType;
import upstart.util.context.Contextualized;
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
  public ExtendedPromise() {
  }

  protected ExtendedPromise(CompletableFuture<Contextualized<T>> completion) {
    super(completion);
  }

  protected abstract Promise.PromiseFactory factory();

  public P tryComplete(Callable<? extends T> completion) {
    return self(super.tryComplete(completion));
  }

  public P tryComplete(T value, ThrowingRunnable completion) {
    return self(super.tryComplete(value, completion));
  }

  public P consumeFailure(ThrowingRunnable runnable) {
    return self(super.consumeFailure(runnable));
  }

  /**
   * Complete this Future with the result of the Future returned by the given {@link Callable} (or any exception that it throws).
   */
  public P tryCompleteWith(Callable<? extends CompletionStage<? extends T>> completion) {
    return self(super.tryCompleteWith(completion));
  }

  public P fulfill(T result) {
    return self(super.fulfill(result));
  }

  /**
   * Complete this Future with the same result as the given {@link CompletionStage completion}.
   *
   * @return self() Promise (which could potentially still be completed via other means)
   */
  public P completeWith(CompletionStage<? extends T> completion) {
    return self(super.completeWith(completion));
  }

  /**
   * If the given {@link CompletionStage completion} completes exceptionally, then complete this Promise with the same
   * exception. Otherwise, this Promise is unaffected.
   *
   * @return this Promise
   */
  public P failWith(CompletionStage<? extends T> completion) {
    return self(super.failWith(completion));
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
    return sameTypeSubsequent(() -> super.uponCompletion(sideEffect));
  }

  public <E extends Throwable> P recover(Class<E> exceptionType, Function<? super E, ? extends T> recovery) {
    return sameTypeSubsequent(() -> super.recover(exceptionType, recovery));
  }

  public P onCancel(Runnable callback) {
    return sameTypeSubsequent(() -> CompletableFutures.whenCancelled(this, callback));
  }

  @Override
  public P whenComplete(BiConsumer<? super T, ? super Throwable> action) {
    return sameTypeSubsequent(() -> super.whenComplete(action));
  }

  @Override
  public P whenCompleteAsync(BiConsumer<? super T, ? super Throwable> action) {
    return sameTypeSubsequent(() -> super.whenCompleteAsync(action));
  }

  @Override
  public P whenCompleteAsync(BiConsumer<? super T, ? super Throwable> action, Executor executor) {
    return sameTypeSubsequent(() -> super.whenCompleteAsync(action, executor));
  }

  @Override
  public P exceptionally(Function<Throwable, ? extends T> fn) {
    return sameTypeSubsequent(() -> super.exceptionally(fn));
  }

  @Override
  public P orTimeout(long timeout, TimeUnit unit) {
    return self(super.orTimeout(timeout, unit));
  }

  @Override
  public P toCompletableFuture() {
    return self();
  }

  protected P sameTypeSubsequent(Supplier<CompletableFuture<T>> superCall) {
    return returningSubsequent(factory(), superCall);
  }

  private P self(CompletableFuture<T> future) {
    assert future == this;
    return self();
  }
}
