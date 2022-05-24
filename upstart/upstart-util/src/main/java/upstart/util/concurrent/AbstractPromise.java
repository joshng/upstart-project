package upstart.util.concurrent;

import upstart.util.SelfType;
import upstart.util.exceptions.FallibleSupplier;
import upstart.util.exceptions.ThrowingRunnable;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public abstract class AbstractPromise<T, P extends AbstractPromise<T, P>> extends CompletableFuture<T> implements BiConsumer<T, Throwable>, SelfType<P> {
  private static final ThreadLocal<PromiseFactory<?>> INCOMPLETE_FUTURE_FACTORY = new ThreadLocal<>();

  protected abstract PromiseFactory<P> factory();

  /**
   * Complete this Future with the result of calling the given {@link Callable}, or with any exception that it throws.
   */
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

  public Promise<Void> toVoid() {
    return thenReplace(null);
  }

  public <U> Promise<U> thenReplace(U value) {
    return thenApply(__ -> value);
  }

  public <U> Promise<U> thenGet(FallibleSupplier<U, ?> supplier) {
    return thenApply(ignored -> supplier.get());
  }

  public <U> Promise<U> thenGetAsync(FallibleSupplier<U, ?> supplier, Executor executor) {
    return thenApplyAsync(ignored -> supplier.get(), executor);
  }

  public <U> Promise<U> thenComposeGet(FallibleSupplier<? extends CompletionStage<U>, ?> supplier) {
    return thenCompose(ignored -> supplier.get());
  }

  public <U> Promise<U> thenComposeGetAsync(FallibleSupplier<? extends CompletionStage<U>, ?> supplier, Executor executor) {
    return thenComposeAsync(ignored -> supplier.get(), executor);
  }

  public <U> OptionalPromise<U> thenApplyOptional(Function<? super T, Optional<U>> fn) {
    return asOptionalPromise(() -> baseApply(fn));
  }

  public <U> OptionalPromise<U> thenComposeOptional(Function<? super T, ? extends CompletionStage<Optional<U>>> fn) {
    return asOptionalPromise(() -> baseCompose(fn));
  }

  public <U> OptionalPromise<U> thenOptionallyCompose(Function<? super T, Optional<? extends CompletionStage<U>>> fn) {
    return asOptionalPromise(() -> baseCompose(result -> fn.apply(result)
            .map(f -> f.thenApply(Optional::ofNullable))
            .orElse(OptionalPromise.empty())));
  }

  public OptionalPromise<T> exceptionAsOptional(Class<? extends Exception> exceptionType) {
    return thenApplyOptional(Optional::ofNullable)
            .exceptionally(e -> CompletableFutures.applyRecovery(
                    e,
                    exceptionType,
                    ignored -> true,
                    ignored -> Optional.empty()
            ));
  }

  public <U> ListPromise<U> thenApplyList(Function<? super T, ? extends List<U>> fn) {
    return asListPromise(() -> baseApply(fn));
  }

  public <U> ListPromise<U> thenComposeList(Function<? super T, ? extends CompletionStage<List<U>>> fn) {
    return asListPromise(() -> baseCompose(fn));
  }

  @SuppressWarnings("unchecked")
  public <U> Promise<U> thenApply(Function<? super T, ? extends U> fn) {
    return (Promise<U>) super.thenApply(fn);
  }

  @SuppressWarnings("unchecked")
  public P onCancel(Runnable callback) {
    return withSideEffect(() -> CompletableFutures.whenCancelled(this, callback));
  }

  public <U> Promise<U> thenReplaceFuture(Supplier<? extends CompletionStage<U>> supplier) {
    return thenCompose(__ -> supplier.get());
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
  public void accept(T t, Throwable throwable) {
    if (throwable == null) {
      complete(t);
    } else {
      completeExceptionally(throwable);
    }
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

  protected <U> CompletableFuture<U> baseApply(Function<? super T, ? extends U> fn) {
    return super.thenApply(fn);
  }

  protected <U> CompletableFuture<U> baseCompose(Function<? super T, ? extends CompletionStage<U>> fn) {
    return super.thenCompose(fn);
  }

  protected P withSideEffect(Supplier<CompletableFuture<T>> superCall) {
    return chainWithFactory(superCall, factory());
  }

  protected <O> OptionalPromise<O> asOptionalPromise(Supplier<CompletableFuture<Optional<O>>> superMethod) {
    return chainWithFactory(superMethod, OptionalPromise::new);
  }

  protected <O> ListPromise<O> asListPromise(Supplier<CompletableFuture<List<O>>> chainMethod) {
    return chainWithFactory(chainMethod, ListPromise::new);
  }

  @SuppressWarnings("unchecked")
  @Override
  public <U> CompletableFuture<U> newIncompleteFuture() {
    PromiseFactory<?> factory = INCOMPLETE_FUTURE_FACTORY.get();
    return factory == null ? new Promise<>() : (CompletableFuture<U>) factory.newIncompleteFuture();
  }

  @SuppressWarnings("unchecked")
  protected static <T, P extends CompletableFuture<T>> P chainWithFactory(
          Supplier<? extends CompletableFuture<T>> superMethod,
          PromiseFactory<P> factory
  ) {
    INCOMPLETE_FUTURE_FACTORY.set(factory);
    try {
      return (P) superMethod.get();
    } finally {
      INCOMPLETE_FUTURE_FACTORY.remove();
    }
  }

  protected interface PromiseFactory<P extends CompletableFuture<?>> {
    P newIncompleteFuture();
  }
}
