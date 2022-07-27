package upstart.util.concurrent;


import upstart.util.collect.Optionals;
import upstart.util.context.AsyncContext;
import upstart.util.context.AsyncLocal;
import upstart.util.context.Contextualized;
import upstart.util.context.ContextualizedFuture;
import upstart.util.exceptions.ThrowingConsumer;
import upstart.util.exceptions.ThrowingRunnable;
import upstart.util.functions.TriFunction;

import upstart.util.exceptions.Try;
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
  private static final ThreadLocalReference<PromiseFactory> INCOMPLETE_PROMISE = new ThreadLocalReference<>();
  private static final PromiseFactory PROMISE_FACTORY = PromiseFactory.of(null, Promise::new);
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

  @SuppressWarnings("unchecked")
  protected static <T, P extends Promise<T>> P returningSubsequent(
          PromiseFactory subsequent,
          Supplier<? extends CompletionStage<? extends T>> superMethod
  ) {
    assert INCOMPLETE_PROMISE.get() == null : "Promise already in progress";
    INCOMPLETE_PROMISE.set(subsequent);
    return (P) superMethod.get();
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

  public <U> OptionalPromise<U> thenGetOptional(Supplier<Optional<U>> supplier, Executor executor) {
    return thenApplyOptional(ignored -> supplier.get());
  }

  public <U> OptionalPromise<U> thenGetOptionalAsync(Supplier<Optional<U>> supplier, Executor executor) {
    return thenApplyOptional(ignored -> supplier.get());
  }

  public <U> Promise<U> thenComposeGet(Supplier<? extends CompletionStage<U>> supplier) {
    return thenCompose(ignored -> supplier.get());
  }

  public <U> Promise<U> thenComposeGetAsync(Supplier<? extends CompletionStage<U>> supplier, Executor executor) {
    return thenComposeAsync(ignored -> supplier.get(), executor);
  }

  public <U> OptionalPromise<U> thenApplyOptional(Function<? super T, Optional<U>> fn) {
    return isCompletedNormally()
            ? OptionalPromise.completed(fn.apply(join()))
            : asOptionalPromise(() -> thenApply(fn));
  }

  public OptionalPromise<T> thenFilterOptional(Predicate<? super T> filter) {
    return isCompletedNormally()
            ? OptionalPromise.completed(Optional.ofNullable(join()).filter(filter))
            : asOptionalPromise(() -> thenApply(v -> Optional.ofNullable(v).filter(filter)));
  }

  public <U> OptionalPromise<U> thenFilterOptional(Class<U> type) {
    return isCompletedNormally()
            ? OptionalPromise.completed(Optionals.asInstance(join(), type))
            : asOptionalPromise(() -> thenApply((Function<? super T, ? extends Optional<U>>) v -> Optionals.asInstance(
                    v,
                    type
            )));
  }

  public <U> OptionalPromise<U> thenComposeOptional(Function<? super T, ? extends CompletionStage<Optional<U>>> fn) {
    return asOptionalPromise(() -> thenCompose(fn));
  }

  public <U> OptionalPromise<U> thenOptionallyCompose(Function<? super T, Optional<? extends CompletableFuture<U>>> fn) {
    Function<T, OptionalPromise<U>> opWrapper = value -> OptionalPromise.toFutureOptional(fn.apply(value));
    return isCompletedNormally()
            ? opWrapper.apply(join())
            : asOptionalPromise(() -> thenCompose(opWrapper));
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
    return asListPromise(() -> thenApply(fn));
  }

  public <U> ListPromise<U> thenStreamToList(Function<? super T, Stream<U>> fn) {
    return new ListPromise<>(completion.thenApply(Contextualized.liftFunction(in -> fn.apply(in).toList())));
  }

  public <U> ListPromise<U> thenComposeList(Function<? super T, ? extends CompletionStage<List<U>>> fn) {
    return asListPromise(() -> thenCompose(fn));
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
    return toPromise(completion.thenApply(Contextualized.liftFunction(fn)));
  }

  @Override
  public <U> Promise<U> thenApplyAsync(Function<? super T, ? extends U> fn) {
    return toPromise(completion.thenApplyAsync(Contextualized.liftFunction(fn)));
  }

  @Override
  public <U> Promise<U> thenApplyAsync(Function<? super T, ? extends U> fn, Executor executor) {
    return toPromise(completion.thenApplyAsync(Contextualized.liftFunction(fn), executor));
  }

  @Override
  public Promise<Void> thenAccept(Consumer<? super T> action) {
    return toPromise(completion.thenApply(Contextualized.liftConsumer(action)));
  }

  @Override
  public Promise<Void> thenAcceptAsync(Consumer<? super T> action) {
    return toPromise(completion.thenApplyAsync(Contextualized.liftConsumer(action)));
  }

  @Override
  public Promise<Void> thenAcceptAsync(Consumer<? super T> action, Executor executor) {
    return toPromise(completion.thenApplyAsync(Contextualized.liftConsumer(action), executor));
  }

  @Override
  public Promise<Void> thenRun(Runnable action) {
    return toPromise(completion.thenApply(Contextualized.liftRunnable(action)));
  }

  @Override
  public Promise<Void> thenRunAsync(Runnable action) {
    return toPromise(completion.thenApplyAsync(Contextualized.liftRunnable(action)));
  }

  @Override
  public Promise<Void> thenRunAsync(Runnable action, Executor executor) {
    return toPromise(completion.thenApplyAsync(Contextualized.liftRunnable(action), executor));
  }

  @Override
  public <U, V> Promise<V> thenCombine(CompletionStage<? extends U> other, BiFunction<? super T, ? super U, ? extends V> fn) {
    return toPromise(completion.thenCombine(Promise.of(other).completion, Contextualized.liftBiFunction(fn)));
  }

  @Override
  public <U, V> Promise<V> thenCombineAsync(CompletionStage<? extends U> other, BiFunction<? super T, ? super U, ? extends V> fn) {
    return toPromise(completion.thenCombineAsync(Promise.of(other).completion, Contextualized.liftBiFunction(fn)));
  }

  @Override
  public <U, V> Promise<V> thenCombineAsync(CompletionStage<? extends U> other, BiFunction<? super T, ? super U, ? extends V> fn, Executor executor) {
    return toPromise(completion.thenCombineAsync(Promise.of(other).completion, Contextualized.liftBiFunction(fn), executor));
  }

  @Override
  public <U> Promise<Void> thenAcceptBoth(CompletionStage<? extends U> other, BiConsumer<? super T, ? super U> action) {
    return thenCombine(other, Contextualized.liftBiConsumer(action));
  }

  @Override
  public <U> Promise<Void> thenAcceptBothAsync(CompletionStage<? extends U> other, BiConsumer<? super T, ? super U> action) {
    return thenCombineAsync(other, Contextualized.liftBiConsumer(action));
  }

  @Override
  public <U> Promise<Void> thenAcceptBothAsync(CompletionStage<? extends U> other, BiConsumer<? super T, ? super U> action, Executor executor) {
    return thenCombineAsync(other, Contextualized.liftBiConsumer(action), executor);
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

  private static <T, U> BiFunction<? super T, ? super U, Void> runAfterBothFunction(Runnable action) {
    return (t, u) -> {
      action.run();
      return null;
    };
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
    return toPromise(completion.thenCompose(Contextualized.liftAsyncFunction(fn)));
  }

  @Override
  public <U> Promise<U> thenComposeAsync(Function<? super T, ? extends CompletionStage<U>> fn) {
    return toPromise(completion.thenComposeAsync(Contextualized.liftAsyncFunction(fn)));
  }

  @Override
  public <U> Promise<U> thenComposeAsync(Function<? super T, ? extends CompletionStage<U>> fn, Executor executor) {
    return toPromise(completion.thenComposeAsync(Contextualized.liftAsyncFunction(fn), executor));
  }

  @Override
  public <U> Promise<U> handle(BiFunction<? super T, Throwable, ? extends U> fn) {
    return toPromise(completion.thenApply(Contextualized.liftHandlerFunction(fn)));
  }

  @Override
  public <U> Promise<U> handleAsync(BiFunction<? super T, Throwable, ? extends U> fn) {
    return toPromise(completion.thenApplyAsync(Contextualized.liftHandlerFunction(fn)));
  }

  @Override
  public <U> Promise<U> handleAsync(BiFunction<? super T, Throwable, ? extends U> fn, Executor executor) {
    return toPromise(completion.thenApplyAsync(Contextualized.liftHandlerFunction(fn), executor));
  }
  @Override
  public Promise<T> whenComplete(BiConsumer<? super T, ? super Throwable> action) {
    return toPromise(completion.whenComplete(whenCompleteFn(action)));
  }

  @Override
  public Promise<T> whenCompleteAsync(BiConsumer<? super T, ? super Throwable> action) {
    return toPromise(completion.whenCompleteAsync(whenCompleteFn(action)));
  }

  @Override
  public Promise<T> whenCompleteAsync(BiConsumer<? super T, ? super Throwable> action, Executor executor) {
    return toPromise(completion.whenCompleteAsync(whenCompleteFn(action), executor));
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
    return toPromise(completion.thenApply(Contextualized.liftRecoverFunction(fn)));
  }

  @Override
  public Promise<T> exceptionallyAsync(Function<Throwable, ? extends T> fn) {
    return toPromise(completion.thenApplyAsync(Contextualized.liftRecoverFunction(fn)));
  }

  @Override
  public Promise<T> exceptionallyAsync(Function<Throwable, ? extends T> fn, Executor executor) {
    return toPromise(completion.thenApplyAsync(Contextualized.liftRecoverFunction(fn), executor));
  }

  @Override
  public Promise<T> exceptionallyCompose(Function<Throwable, ? extends CompletionStage<T>> fn) {
    return toPromise(completion.thenCompose(Contextualized.liftAsyncRecoverFunction(fn)));
  }

  @Override
  public Promise<T> exceptionallyComposeAsync(Function<Throwable, ? extends CompletionStage<T>> fn) {
    return toPromise(completion.thenComposeAsync(Contextualized.liftAsyncRecoverFunction(fn)));
  }

  @Override
  public Promise<T> exceptionallyComposeAsync(Function<Throwable, ? extends CompletionStage<T>> fn, Executor executor) {
    return toPromise(completion.thenComposeAsync(Contextualized.liftAsyncRecoverFunction(fn), executor));
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

  protected static <O> OptionalPromise<O> asOptionalPromise(Supplier<CompletableFuture<Optional<O>>> chainMethod) {
    return returningSubsequent(OptionalPromise.OPTIONAL_PROMISE_FACTORY, chainMethod);
  }

  protected static <O> ListPromise<O> asListPromise(Supplier<CompletableFuture<List<O>>> chainMethod) {
    return returningSubsequent(ListPromise.LIST_PROMISE_FACTORY, chainMethod);
  }

  private static <U> Promise<U> toPromise(CompletableFuture<Contextualized<U>> contextualizedFuture) {
    PromiseFactory promiseFactory = INCOMPLETE_PROMISE.get();
    if (promiseFactory == null) {
      return new Promise<>(contextualizedFuture);
    } else {
      INCOMPLETE_PROMISE.remove();
      return promiseFactory.newPromise(contextualizedFuture);
    }
  }

  @SuppressWarnings("rawtypes")
  protected abstract static class PromiseFactory {
    private final Object emptyValue;
    private final Promise emptyInstance;
    private final Promise canceledInstance;

    protected PromiseFactory(Object emptyValue) {
      this.emptyValue = emptyValue;
      emptyInstance = newPromise(ContextualizedFuture.of(emptyValue, AsyncContext.EMPTY));
      canceledInstance = newPromise(new ContextualizedFuture<>());
      canceledInstance.cancel(false);
    }

    protected static <V> PromiseFactory of(V emptyValue, Function<CompletableFuture<Contextualized<V>>, ? extends Promise<V>> constructor) {
      return new PromiseFactory(emptyValue) {
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
  }
}
