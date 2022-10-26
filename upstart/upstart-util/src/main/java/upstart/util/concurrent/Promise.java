package upstart.util.concurrent;


import upstart.util.collect.Optionals;
import upstart.util.context.AsyncContext;
import upstart.util.context.AsyncLocal;
import upstart.util.context.Contextualized;
import upstart.util.context.ContextualizedFuture;
import upstart.util.exceptions.ThrowingConsumer;
import upstart.util.exceptions.ThrowingRunnable;

import upstart.util.exceptions.Try;
import upstart.util.functions.QuadFunction;
import upstart.util.functions.TriFunction;
import upstart.util.reflect.Reflect;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * An extension of {@link CompletableFuture} with utility methods for completing a "Promise" in various ways, while
 * retaining the values of all {@link AsyncLocal}s across completions.
 */
public class Promise<T> extends CompletableFuture<T> implements BiConsumer<T, Throwable> {
  private static final PromiseFactory PROMISE_FACTORY = PromiseFactory.of(Promise.class, null, Promise::new);
  private final CompletableFuture<Contextualized<T>> completion;

  public Promise() {
    completion = ContextualizedFuture.<T>contextualizeResult(super::whenComplete);
    arrangeCompletion();
  }

  protected Promise(CompletableFuture<Contextualized<T>> completion) {
    this.completion = completion;
    arrangeCompletion();
  }

  public CompletableFuture<Contextualized<T>> contextualizedFuture() {
    return completion;
  }

  private void arrangeCompletion() {
    // necessary to enact the expected behavior of isDone(), isCompletedExceptionally(), etc
    completion.thenAccept(contextualized -> contextualized.value().accept((v, e) -> {
      if (e != null) {
        super.completeExceptionally(e);
      } else {
        super.complete(v);
      }
    }));
  }

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

  @SuppressWarnings("unchecked")
  public static <T> Promise<T> of(CompletionStage<T> stage) {
    return (stage instanceof Promise promise) ? promise : new Promise<>(ContextualizedFuture.captureContext(stage));
  }

  public static <T> Promise<T> completed(T result) {
    return new Promise<T>().fulfill(result);
  }


  public static <T> Promise<T> nullPromise() {
    return PROMISE_FACTORY.emptyInstance();
  }

  public static <T> Promise<T> canceledPromise() {
    return PROMISE_FACTORY.canceledInstance();
  }

  public static <T> OptionalPromise<T> completed(Optional<T> optional) {
    return OptionalPromise.completed(optional);
  }

  public static <T> ListPromise<T> completed(List<T> list) {
    return ListPromise.completed(list);
  }

  public static <T> Promise<T> failedPromise(Throwable error) {
    Promise<T> promise = new Promise<>();
    promise.completeExceptionally(error);
    return promise;
  }

  public static Promise<Void> allOf(Stream<? extends CompletableFuture<?>> futures) {
    return allOf(CompletableFutures.toArray(futures));
  }

  public static <A, B, O> Promise<O> combine(
          CompletableFuture<A> a,
          CompletableFuture<B> b,
          BiFunction<? super A, ? super B, O> combiner
  ) {
    return Promise.of(a).thenCombine(b, combiner);
  }

  public static <A, B, C, O> Promise<O> combine(
          CompletableFuture<A> a,
          CompletableFuture<B> b,
          CompletableFuture<C> c,
          TriFunction<? super A, ? super B, ? super C, O> combiner
  ) {
    return allOf(a, b, c).thenApply(results -> combiner.apply(a.join(), b.join(), c.join()));
  }

  public static <A, B, C, D, O> Promise<O> combine(
          CompletableFuture<A> a,
          CompletableFuture<B> b,
          CompletableFuture<C> c,
          CompletableFuture<D> d,
          QuadFunction<? super A, ? super B, ? super C, ? super D, O> combiner
  ) {
    return allOf(a, b, c).thenApply(results -> combiner.apply(a.join(), b.join(), c.join(), d.join()));
  }

  public static <A, B, O> Promise<O> combineCompose(
          CompletableFuture<A> a,
          CompletableFuture<B> b,
          BiFunction<? super A, ? super B,  ? extends CompletionStage<O>> combiner
  ) {
    return allOf(a, b).thenCompose(results -> combiner.apply(a.join(), b.join()));
  }

  public static <A, B, C, O> Promise<O> combineCompose(
          CompletableFuture<A> a,
          CompletableFuture<B> b,
          CompletableFuture<C> c,
          TriFunction<? super A, ? super B, ? super C, ? extends CompletionStage<O>> combiner
  ) {
    return allOf(a, b, c).thenCompose(results -> combiner.apply(a.join(), b.join(), c.join()));
  }

  public static <A, B, C, D, O> Promise<O> combineCompose(
          CompletableFuture<A> a,
          CompletableFuture<B> b,
          CompletableFuture<C> c,
          CompletableFuture<D> d,
          QuadFunction<? super A, ? super B, ? super C, ? super D, ? extends CompletionStage<O>> combiner
  ) {
    return allOf(a, b, c, d).thenCompose(results -> combiner.apply(a.join(), b.join(), c.join(), d.join()));
  }

  public static Promise<Void> allOf(CompletableFuture<?>... futures) {
    if (futures.length == 0) return nullPromise();
    if (futures.length == 1) return of(futures[0]).toVoid();

    CompletableFuture<Contextualized<Void>>[] contexts = CompletableFutures.toArray(
            Arrays.stream(futures)
                    .map(ContextualizedFuture::captureContext)
                    .map(f -> f.thenApply(ctx -> ctx.map(ignored -> null))) // discard results, just need context
    );

    CompletableFuture<Void> wrappersDone = CompletableFuture.allOf(futures).exceptionally(e -> null);
    CompletableFuture<Contextualized<Void>> cf = CompletableFuture.allOf(contexts)
            .thenCombine(wrappersDone,
                         (ignored1, ignored2) -> Arrays.stream(contexts)
                                 .map(CompletableFuture::join)
                                 .reduce(Contextualized::mergeContexts).orElseThrow()
            );
    return new Promise<>(cf);
  }

  public static <T> Promise<T> completeAsync(Callable<? extends CompletionStage<? extends T>> completionSupplier) {
    return completeAsync(completionSupplier, ForkJoinPool.commonPool());
  }

  public static <T> Promise<T> completeAsync(Callable<? extends CompletionStage<? extends T>> completionSupplier, Executor executor) {
    return Promise.thatCompletes(promise -> CompletableFuture.runAsync(
            AsyncContext.snapshot().wrapRunnable(() -> promise.tryCompleteWith(completionSupplier)), executor)
    );
  }

  public static <T> Promise<T> callAsync(Callable<? extends T> callable, Executor executor) {
    return Promise.thatCompletes(promise -> CompletableFuture.runAsync(
            AsyncContext.snapshot().wrapRunnable(() -> promise.tryComplete(callable)), executor)
    );
  }

  /**
   * Complete this Future with the result of calling the given {@link Callable}, or with any exception that it throws.
   */
  public Promise<T> tryComplete(Callable<? extends T> completion) {
    return consumeFailure(() -> complete(completion.call()));
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
   *
   * @return self() Promise (which could potentially still be completed via other means)
   */
  public Promise<T> completeWith(CompletionStage<? extends T> completion) {
    completion.whenComplete(this);
    return this;
  }

  /**
   * If the given {@link CompletionStage completion} completes exceptionally, then complete this Promise with the same
   * exception. Otherwise, this Promise is unaffected.
   *
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
   * <p>
   * Note that this uses {@link #whenComplete} to invoke the sideEffect, which implies that the returned {@link Promise}
   * will not reflect any exception that may be thrown by the sideEffect!
   *
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

  public <E extends Throwable> Promise<T> recover(Class<E> exceptionType, Function<? super E, ? extends T> recovery) {
    return (Promise<T>) CompletableFutures.recover(this, exceptionType, recovery);
  }

  public <E extends Throwable> Promise<T> recoverCompose(Class<E> exceptionType, Function<? super E, ? extends CompletionStage<? extends T>> recovery) {
    return CompletableFutures.recoverCompose(this, exceptionType, recovery);
  }

  public <U> Promise<U> thenGet(Supplier<U> supplier) {
    return thenApply(ignored -> supplier.get());
  }

  public <U> Promise<U> thenGetAsync(Supplier<U> supplier, Executor executor) {
    return thenApplyAsync(ignored -> supplier.get(), executor);
  }

  public <U> OptionalPromise<U> thenGetOptional(Supplier<Optional<U>> supplier) {
    return thenApplyOptional(ignored -> supplier.get());
  }

  public <U> OptionalPromise<U> thenGetOptionalAsync(Supplier<Optional<U>> supplier, Executor executor) {
    return thenApplyOptionalAsync(ignored -> supplier.get(), executor);
  }

  public <U> Promise<U> thenComposeGet(Supplier<? extends CompletionStage<U>> supplier) {
    return thenCompose(ignored -> supplier.get());
  }

  public <U> Promise<U> thenComposeGetAsync(Supplier<? extends CompletionStage<U>> supplier, Executor executor) {
    return thenComposeAsync(ignored -> supplier.get(), executor);
  }

  public <U> OptionalPromise<U> thenApplyOptional(Function<? super T, Optional<U>> fn) {
    return thenApplyPromise(OptionalPromise.OPTIONAL_PROMISE_FACTORY, Contextualized.liftFunction(fn));
  }

  public <U> OptionalPromise<U> thenApplyOptionalAsync(Function<? super T, Optional<U>> fn, Executor executor) {
    return thenApplyAsyncPromise(OptionalPromise.OPTIONAL_PROMISE_FACTORY, Contextualized.liftFunction(fn), executor);
  }

  public OptionalPromise<T> thenFilterOptional(Predicate<? super T> filter) {
    return thenApplyOptional(v -> Optional.ofNullable(v).filter(filter));
  }

  public <U> OptionalPromise<U> thenFilterOptional(Class<U> type) {
    return thenApplyOptional(v -> Optionals.asInstance(v, type));
  }

  public <U> OptionalPromise<U> thenComposeOptional(Function<? super T, ? extends CompletionStage<Optional<U>>> fn) {
    return thenComposePromise(OptionalPromise.OPTIONAL_PROMISE_FACTORY, Contextualized.liftAsyncFunction(fn));
  }

  public <U> OptionalPromise<U> thenOptionallyCompose(Function<? super T, Optional<? extends CompletableFuture<U>>> fn) {
    return thenComposeOptional((Function<T, OptionalPromise<U>>) value -> OptionalPromise.toFutureOptional(fn.apply(value)));
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
    return thenApplyPromise(ListPromise.LIST_PROMISE_FACTORY, Contextualized.liftFunction(fn));
  }

  public <U> ListPromise<U> thenStreamToList(Function<? super T, Stream<U>> fn) {
    return thenApplyPromise(ListPromise.LIST_PROMISE_FACTORY, Contextualized.liftFunction(in -> fn.apply(in).toList()));
  }

  public <U> ListPromise<U> thenComposeList(Function<? super T, ? extends CompletionStage<List<U>>> fn) {
    return thenComposePromise(ListPromise.LIST_PROMISE_FACTORY, Contextualized.liftAsyncFunction(fn));
  }

  public <A, B, O> Promise<O> thenCombine(
          CompletionStage<? extends A> a,
          CompletionStage<? extends B> b,
          TriFunction<? super T, ? super A, ? super B, O> fn
  ) {
    return combine(this, a.toCompletableFuture(), b.toCompletableFuture(), fn);
  }

  public <A, B, C, O> Promise<O> thenCombine(
          CompletionStage<? extends A> a,
          CompletionStage<? extends B> b,
          CompletionStage<? extends C> c,
          QuadFunction<? super T, ? super A, ? super B, ? super C, O> fn
  ) {
    return combine(this, a.toCompletableFuture(), b.toCompletableFuture(), c.toCompletableFuture(), fn);
  }

  // could be named thenComposeWith?
  public <A, O> Promise<O> thenCombineCompose(
          CompletionStage<? extends A> a,
          BiFunction<? super T, ? super A, ? extends CompletionStage<O>> fn
  ) {
    return combineCompose(this, a.toCompletableFuture(), fn);
  }

  public <A, B, O> Promise<O> thenCombineCompose(
          CompletionStage<? extends A> a,
          CompletionStage<? extends B> b,
          TriFunction<? super T, ? super A, ? super B, ? extends CompletionStage<O>> fn
  ) {
    return combineCompose(this, a.toCompletableFuture(), b.toCompletableFuture(), fn);
  }

  public Promise<T> onCancel(Runnable callback) {
    return (Promise<T>) CompletableFutures.whenCancelled(this, callback);
  }

  public <U> Promise<U> thenReplaceFuture(Supplier<? extends CompletionStage<U>> supplier) {
    return thenCompose(__ -> supplier.get());
  }

  public Promise<AsyncContext> completionContext() {
    return Promise.of(completion.thenApply(Contextualized::contextSnapshot));
  }

  @Override
  public void accept(T t, Throwable throwable) {
    if (throwable == null) {
      complete(t);
    } else {
      completeExceptionally(throwable);
    }
  }

  public boolean isCompletedNormally() {
    return isDone() && !isCompletedExceptionally();
  }

  ///////////////////// CompletableFuture methods /////////////////////

  @Override
  public boolean complete(T value) {
    return completion.complete(Contextualized.value(value));
  }

  @Override
  public boolean cancel(boolean mayInterruptIfRunning) {
    return completion.complete(Contextualized.canceled());
  }

  @Override
  public boolean completeExceptionally(Throwable ex) {
    return completion.complete(Contextualized.failure(ex));
  }

  @Override
  public T join() {
    try {
      return super.join();
    } finally {
      applyCompletionContext();
    }
  }

  @Override
  public T get() throws InterruptedException, ExecutionException {
    boolean haveResult = true;
    try {
      return super.get();
    } catch (InterruptedException e) {
      haveResult = false;
      Thread.currentThread().interrupt();
      throw e;
    } finally {
      if (haveResult) applyCompletionContext();
    }
  }

  @Override
  public T get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
    boolean haveResult = true;
    try {
      return super.get(timeout, unit);
    } catch (TimeoutException e) {
      haveResult = false;
      throw e;
    } finally {
      if (haveResult) applyCompletionContext();
    }
  }

  private static final Object NO_VALUE = new Object();

  @SuppressWarnings("unchecked")
  @Override
  public T getNow(T valueIfAbsent) {
    boolean haveResult = true;
    T result;
    try {
      result = super.getNow((T) NO_VALUE); // if this throws, then haveResult = true
      haveResult = result != NO_VALUE;
      return haveResult ? result : valueIfAbsent;
    } finally {
      // only apply the context if the caller will observe the actual result
      if (haveResult) applyCompletionContext();
    }
  }

  private void applyCompletionContext() {
    if (completion.isDone()) completion.join().contextSnapshot().applyToCurrent();
  }

  ///////////////////// CompletionStage /////////////////////
  @Override
  public <U> Promise<U> thenApply(Function<? super T, ? extends U> fn) {
    return thenApplyPromise(PROMISE_FACTORY, Contextualized.liftFunction(fn));
  }

  @Override
  public <U> Promise<U> thenApplyAsync(Function<? super T, ? extends U> fn) {
    return thenApplyAsyncPromise(PROMISE_FACTORY, Contextualized.liftFunction(fn));
  }

  @Override
  public <U> Promise<U> thenApplyAsync(Function<? super T, ? extends U> fn, Executor executor) {
    return thenApplyAsyncPromise(PROMISE_FACTORY, Contextualized.liftFunction(fn), executor);
  }

  @Override
  public Promise<Void> thenAccept(Consumer<? super T> action) {
    return thenApplyPromise(PROMISE_FACTORY, Contextualized.liftConsumer(action));
  }

  @Override
  public Promise<Void> thenAcceptAsync(Consumer<? super T> action) {
    return thenApplyAsyncPromise(PROMISE_FACTORY, Contextualized.liftConsumer(action));
  }

  @Override
  public Promise<Void> thenAcceptAsync(Consumer<? super T> action, Executor executor) {
    return thenApplyAsyncPromise(PROMISE_FACTORY, Contextualized.liftConsumer(action), executor);
  }

  @Override
  public Promise<Void> thenRun(Runnable action) {
    return thenApplyPromise(PROMISE_FACTORY, Contextualized.liftRunnable(action));
  }

  @Override
  public Promise<Void> thenRunAsync(Runnable action) {
    return thenApplyAsyncPromise(PROMISE_FACTORY, Contextualized.liftRunnable(action));
  }

  @Override
  public Promise<Void> thenRunAsync(Runnable action, Executor executor) {
    return thenApplyAsyncPromise(PROMISE_FACTORY, Contextualized.liftRunnable(action), executor);
  }

  @Override
  public <U, V> Promise<V> thenCombine(CompletionStage<? extends U> other, BiFunction<? super T, ? super U, ? extends V> fn) {
    return thenCombinePromise(PROMISE_FACTORY, other, Contextualized.liftBiFunction(fn));
  }


  @Override
  public <U, V> Promise<V> thenCombineAsync(CompletionStage<? extends U> other, BiFunction<? super T, ? super U, ? extends V> fn) {
    return PROMISE_FACTORY.newPromise(completion.thenCombineAsync(Promise.of(other).completion, Contextualized.liftBiFunction(fn)));
  }

  @Override
  public <U, V> Promise<V> thenCombineAsync(CompletionStage<? extends U> other, BiFunction<? super T, ? super U, ? extends V> fn, Executor executor) {
    return PROMISE_FACTORY.newPromise(completion.thenCombineAsync(Promise.of(other).completion, Contextualized.liftBiFunction(fn), executor));
  }

  @Override
  public <U> Promise<Void> thenAcceptBoth(CompletionStage<? extends U> other, BiConsumer<? super T, ? super U> action) {
    return thenCombine(other, acceptBothFunction(action));
  }

  @Override
  public <U> Promise<Void> thenAcceptBothAsync(CompletionStage<? extends U> other, BiConsumer<? super T, ? super U> action) {
    return thenCombineAsync(other, acceptBothFunction(action));
  }

  @Override
  public <U> Promise<Void> thenAcceptBothAsync(CompletionStage<? extends U> other, BiConsumer<? super T, ? super U> action, Executor executor) {
    return thenCombineAsync(other, acceptBothFunction(action), executor);
  }

  //
  @Override
  public Promise<Void> runAfterBoth(CompletionStage<?> other, Runnable action) {
    return thenCombine(other, runAfterBothFunction(action));
  }

  @Override
  public Promise<Void> runAfterBothAsync(CompletionStage<?> other, Runnable action) {
    return thenCombineAsync(other, runAfterBothFunction(action));
  }

  @Override
  public Promise<Void> runAfterBothAsync(CompletionStage<?> other, Runnable action, Executor executor) {
    return thenCombineAsync(other, runAfterBothFunction(action), executor);
  }

  @Override
  public <U> Promise<U> applyToEither(CompletionStage<? extends T> other, Function<? super T, U> fn) {
    return or(other).thenApply(fn);
  }

  @Override
  public <U> Promise<U> applyToEitherAsync(CompletionStage<? extends T> other, Function<? super T, U> fn) {
    return or(other).thenApplyAsync(fn);
  }

  @Override
  public <U> Promise<U> applyToEitherAsync(CompletionStage<? extends T> other, Function<? super T, U> fn, Executor executor) {
    return or(other).thenApplyAsync(fn, executor);
  }

  public Promise<T> or(CompletionStage<? extends T> other) {
    return Promise.thatCompletes(promise -> promise.completeWith(this).completeWith(other));
  }

  @Override
  public Promise<Void> acceptEither(CompletionStage<? extends T> other, Consumer<? super T> action) {
    return or(other).thenAccept(action);
  }

  @Override
  public Promise<Void> acceptEitherAsync(CompletionStage<? extends T> other, Consumer<? super T> action) {
    return or(other).thenAcceptAsync(action);
  }

  @Override
  public Promise<Void> acceptEitherAsync(CompletionStage<? extends T> other, Consumer<? super T> action, Executor executor) {
    return or(other).thenAcceptAsync(action, executor);
  }

  @SuppressWarnings("unchecked")
  @Override
  public Promise<Void> runAfterEither(CompletionStage<?> other, Runnable action) {
    return or((CompletionStage<? extends T>) other).thenRun(action);
  }

  @SuppressWarnings("unchecked")
  @Override
  public Promise<Void> runAfterEitherAsync(CompletionStage<?> other, Runnable action) {
    return or((CompletionStage<? extends T>) other).thenRunAsync(action);
  }

  @SuppressWarnings("unchecked")
  @Override
  public Promise<Void> runAfterEitherAsync(CompletionStage<?> other, Runnable action, Executor executor) {
    return or((CompletionStage<? extends T>) other).thenRunAsync(action, executor);
  }

  //TODO: test error-handling context
  @Override
  public <U> Promise<U> thenCompose(Function<? super T, ? extends CompletionStage<U>> fn) {
    return thenComposePromise(PROMISE_FACTORY, Contextualized.liftAsyncFunction(fn));
  }

  @Override
  public <U> Promise<U> thenComposeAsync(Function<? super T, ? extends CompletionStage<U>> fn) {
    return PROMISE_FACTORY.newPromise(completion.thenComposeAsync(Contextualized.liftAsyncFunction(fn)));
  }

  @Override
  public <U> Promise<U> thenComposeAsync(Function<? super T, ? extends CompletionStage<U>> fn, Executor executor) {
    return PROMISE_FACTORY.newPromise(completion.thenComposeAsync(Contextualized.liftAsyncFunction(fn), executor));
  }

  @Override
  public <U> Promise<U> handle(BiFunction<? super T, Throwable, ? extends U> fn) {
    return PROMISE_FACTORY.newPromise(completion.thenApply(Contextualized.liftHandlerFunction(fn)));
  }

  @Override
  public <U> Promise<U> handleAsync(BiFunction<? super T, Throwable, ? extends U> fn) {
    return PROMISE_FACTORY.newPromise(completion.thenApplyAsync(Contextualized.liftHandlerFunction(fn)));
  }

  @Override
  public <U> Promise<U> handleAsync(BiFunction<? super T, Throwable, ? extends U> fn, Executor executor) {
    return PROMISE_FACTORY.newPromise(completion.thenApplyAsync(Contextualized.liftHandlerFunction(fn), executor));
  }
  @Override
  public Promise<T> whenComplete(BiConsumer<? super T, ? super Throwable> action) {
    return sameTypeSubsequentFactory().newPromise(completion.whenComplete(whenCompleteFn(action)));
  }

  @Override
  public Promise<T> whenCompleteAsync(BiConsumer<? super T, ? super Throwable> action) {
    return sameTypeSubsequentFactory().newPromise(completion.whenCompleteAsync(whenCompleteFn(action)));
  }

  @Override
  public Promise<T> whenCompleteAsync(BiConsumer<? super T, ? super Throwable> action, Executor executor) {
    return sameTypeSubsequentFactory().newPromise(completion.whenCompleteAsync(whenCompleteFn(action), executor));
  }

  private static <T> BiConsumer<Contextualized<T>, Throwable> whenCompleteFn(BiConsumer<? super T, ? super Throwable> action) {
    return (ctx, ignored) -> ctx.accept(action);
  }

  @Override
  public Promise<T> toCompletableFuture() {
    return this;
  }

  @Override
  public Promise<T> exceptionally(Function<Throwable, ? extends T> fn) {
    return sameTypeSubsequentFactory().newPromise(completion.thenApply(Contextualized.liftRecoverFunction(fn)));
  }

  @Override
  public Promise<T> exceptionallyAsync(Function<Throwable, ? extends T> fn) {
    return sameTypeSubsequentFactory().newPromise(completion.thenApplyAsync(Contextualized.liftRecoverFunction(fn)));
  }

  @Override
  public Promise<T> exceptionallyAsync(Function<Throwable, ? extends T> fn, Executor executor) {
    return sameTypeSubsequentFactory().newPromise(completion.thenApplyAsync(Contextualized.liftRecoverFunction(fn), executor));
  }

  @Override
  public Promise<T> exceptionallyCompose(Function<Throwable, ? extends CompletionStage<T>> fn) {
    return sameTypeSubsequentFactory().newPromise(completion.thenCompose(Contextualized.liftAsyncRecoverFunction(fn)));
  }

  @Override
  public Promise<T> exceptionallyComposeAsync(Function<Throwable, ? extends CompletionStage<T>> fn) {
    return sameTypeSubsequentFactory().newPromise(completion.thenComposeAsync(Contextualized.liftAsyncRecoverFunction(fn)));
  }

  @Override
  public Promise<T> exceptionallyComposeAsync(Function<Throwable, ? extends CompletionStage<T>> fn, Executor executor) {
    return sameTypeSubsequentFactory().newPromise(completion.thenComposeAsync(Contextualized.liftAsyncRecoverFunction(fn), executor));
  }

  @Override
  public Promise<T> orTimeout(long timeout, TimeUnit unit) {
    completion.orTimeout(timeout, unit);
    return this;
  }

  @Override
  public void obtrudeException(Throwable ex) {
    completion.obtrudeValue(Contextualized.failure(ex));
    super.obtrudeException(ex);
  }

  @Override
  public void obtrudeValue(T value) {
    completion.obtrudeValue(Contextualized.value(value));
    super.obtrudeValue(value);
  }

  protected PromiseFactory sameTypeSubsequentFactory() {
    return PROMISE_FACTORY;
  }

  @SuppressWarnings("unchecked")
  protected <O, P extends Promise<O>> P thenApplyPromise(PromiseFactory promiseFactory, Contextualized.ContextualFunction<T, O> fn) {
    return (P) promiseFactory.newPromise(completion.thenApply(fn));
  }

  @SuppressWarnings("unchecked")
  protected <O, P extends Promise<O>> P thenApplyAsyncPromise(PromiseFactory promiseFactory, Contextualized.ContextualFunction<T, O> fn) {
    return (P) promiseFactory.newPromise(completion.thenApplyAsync(fn));
  }

  @SuppressWarnings("unchecked")
  protected <O, P extends Promise<O>> P thenApplyAsyncPromise(PromiseFactory promiseFactory, Contextualized.ContextualFunction<T, O> fn, Executor executor) {
    return (P) promiseFactory.newPromise(completion.thenApplyAsync(fn, executor));
  }

  @SuppressWarnings("unchecked")
  protected <U, P extends Promise<U>> P thenComposePromise(
          PromiseFactory promiseFactory, Contextualized.ContextualAsyncFunction<T, U> fn
  ) {
    return (P) promiseFactory.newPromise(completion.thenCompose(fn));
  }

  @SuppressWarnings("unchecked")
  protected <U, V, P extends Promise<V>> P thenCombinePromise(
          PromiseFactory promiseFactory,
          CompletionStage<? extends U> other,
          BiFunction<Contextualized<T>, Contextualized<? extends U>, Contextualized<V>> fn
  ) {
    return (P) promiseFactory.newPromise(completion.thenCombine(Promise.of(other).completion, fn));
  }

  private static <T, U> BiFunction<? super T, ? super U, Void> runAfterBothFunction(Runnable action) {
    return (t, u) -> {
      action.run();
      return null;
    };
  }

  private static <T, U> BiFunction<T, U, Void> acceptBothFunction(BiConsumer<? super T, ? super U> action) {
    return (t, u) -> {
      action.accept(t, u);
      return null;
    };
  }

  @SuppressWarnings("rawtypes")
  protected abstract static class PromiseFactory {
    private final Class<? extends Promise> factoryType;
    private final Object emptyValue;
    private final Promise emptyInstance;
    private final Promise canceledInstance;

    protected PromiseFactory(Class<? extends Promise> factoryType, Object emptyValue) {
      this.factoryType = factoryType;
      this.emptyValue = emptyValue;
      emptyInstance = newPromise(ContextualizedFuture.of(emptyValue, AsyncContext.EMPTY));
      canceledInstance = newPromise(new ContextualizedFuture<>());
      canceledInstance.cancel(false);
    }

    protected static <V> PromiseFactory of(Class<? extends Promise> promiseType, V emptyValue, Function<CompletableFuture<Contextualized<V>>, ? extends Promise<V>> constructor) {
      return new PromiseFactory(promiseType, emptyValue) {
        @SuppressWarnings("unchecked")
        @Override
        public <T> Promise<T> newPromise(CompletableFuture<Contextualized<T>> future) {
          return (Promise<T>) constructor.apply(Reflect.blindCast(future));
        }
      };
    }

    public abstract <T> Promise<T> newPromise(CompletableFuture<Contextualized<T>> contextualizedFuture);

    @SuppressWarnings("unchecked")
    public <T, P extends Promise<T>> P emptyInstance() {
      AsyncContext context = AsyncContext.snapshot();
      return (P) (context.isEmpty() ? emptyInstance : newPromise(ContextualizedFuture.of(emptyValue, context)));
    }

    @SuppressWarnings("unchecked")
    public <T, P extends Promise<T>> P canceledInstance() {
      AsyncContext context = AsyncContext.snapshot();
      return (P) (context.isEmpty()
              ? canceledInstance
              : newPromise(ContextualizedFuture.completed(new Contextualized<>(Try.failure(new CancellationException()), context))));
    }

    @Override
    public String toString() {
      return "PromiseFactory{" + factoryType.getSimpleName() + '}';
    }
  }
}
