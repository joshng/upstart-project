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

public abstract class ExtendedPromise<T, P extends ExtendedPromise<T, P>> extends Promise<T> implements SelfType<P> {
  public ExtendedPromise() {
  }

  protected ExtendedPromise(CompletableFuture<Contextualized<T>> completion) {
    super(completion);
  }

  public P tryComplete(Callable<? extends T> completion) {
    return selfType(super.tryComplete(completion));
  }

  public P tryComplete(T value, ThrowingRunnable completion) {
    return selfType(super.tryComplete(value, completion));
  }

  public P consumeFailure(ThrowingRunnable runnable) {
    return selfType(super.consumeFailure(runnable));
  }

  /**
   * Complete this Future with the result of the Future returned by the given {@link Callable} (or any exception that it throws).
   */
  public P tryCompleteWith(Callable<? extends CompletionStage<? extends T>> completion) {
    return selfType(super.tryCompleteWith(completion));
  }

  public P fulfill(T result) {
    return selfType(super.fulfill(result));
  }

  /**
   * Complete this Future with the same result as the given {@link CompletionStage completion}.
   *
   * @return self() Promise (which could potentially still be completed via other means)
   */
  public P completeWith(CompletionStage<? extends T> completion) {
    return selfType(super.completeWith(completion));
  }

  /**
   * If the given {@link CompletionStage completion} completes exceptionally, then complete this Promise with the same
   * exception. Otherwise, this Promise is unaffected.
   *
   * @return this Promise
   */
  public P failWith(CompletionStage<? extends T> completion) {
    return selfType(super.failWith(completion));
  }

  public <E extends Throwable> P recover(Class<E> exceptionType, Function<? super E, ? extends T> recovery) {
    return selfType(super.recover(exceptionType, recovery));
  }

  public P onCancel(Runnable callback) {
    return selfType(super.onCancel(callback));
  }

  @Override
  public P whenComplete(BiConsumer<? super T, ? super Throwable> action) {
    return selfType(super.whenComplete(action));
  }

  @Override
  public P whenCompleteAsync(BiConsumer<? super T, ? super Throwable> action) {
    return selfType(super.whenCompleteAsync(action));
  }

  @Override
  public P whenCompleteAsync(BiConsumer<? super T, ? super Throwable> action, Executor executor) {
    return selfType(super.whenCompleteAsync(action, executor));
  }

  @Override
  public P exceptionally(Function<Throwable, ? extends T> fn) {
    return selfType(super.exceptionally(fn));
  }

  @Override
  public P orTimeout(long timeout, TimeUnit unit) {
    return selfType(super.orTimeout(timeout, unit));
  }

  @Override
  public P toCompletableFuture() {
    return self();
  }


  @SuppressWarnings("unchecked")
  private P selfType(CompletableFuture<?> future) {
    return (P) future;
  }
}
