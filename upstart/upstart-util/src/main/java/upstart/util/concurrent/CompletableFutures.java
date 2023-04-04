package upstart.util.concurrent;

import com.google.common.base.MoreObjects;
import com.google.common.util.concurrent.UncheckedExecutionException;

import java.lang.reflect.InvocationTargetException;
import java.time.Duration;
import java.util.Collection;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.google.common.base.Preconditions.checkState;

public class CompletableFutures {
  private static final Promise CANCELLED_FUTURE = new Promise<Void>() {{
    cancel(true);
  }};

  public static <T> Promise<T> nullFuture() {
    return Promise.nullPromise();
  }

  public static <T> OptionalPromise<T> emptyFuture() {
    return OptionalPromise.empty();
  }

  public static <T> ListPromise<T> emptyListFuture() {
    return ListPromise.empty();
  }

  @SuppressWarnings("unchecked")
  public static <T> Promise<T> cancelledFuture() {
    return CANCELLED_FUTURE;
  }

  public static <T> Promise<T> failedFuture(Throwable t) {
    return Promise.failedPromise(t);
  }

  public static CompletableFuture<Void> runSafely(Runnable runnable) {
    try {
      runnable.run();
      return CompletableFutures.nullFuture();
    } catch (Throwable t) {
      return CompletableFutures.failedFuture(t);
    }
  }

  public static <T> CompletableFuture<T> callSafely(Callable<? extends CompletionStage<T>> runnable) {
    try {
      return runnable.call().toCompletableFuture();
    } catch (Throwable t) {
      return CompletableFutures.failedFuture(t);
    }
  }

  public static <T> Promise<T> composeAsync(Callable<? extends CompletableFuture<T>> asyncSupplier, Executor executor) {
    return sequence(CompletableFuture.supplyAsync(() -> callSafely(asyncSupplier), executor));
  }

  public static boolean isDoneWithin(Duration timeout, Future<?> future) throws InterruptedException {
    if (!future.isDone()) {
      try {
        future.get(timeout.toNanos(), TimeUnit.NANOSECONDS);
      } catch (TimeoutException ignored) {
        return false;
      } catch (ExecutionException ignored) {
        // ignored, the caller will obtain this exception from the future
      }
    }
    return true;
  }

  public static boolean isCompletedNormally(CompletableFuture<?> future) {
    return future.isDone() && !future.isCompletedExceptionally();
  }

  public static Optional<Throwable> getException(CompletableFuture<?> future) {
    if (future.isCompletedExceptionally()) {
      try {
        future.join();
      } catch (CompletionException e) {
        return Optional.of(e.getCause());
      } catch (Exception e) {
        return Optional.of(e);
      }
    }
    return Optional.empty();
  }

  public static Promise<Void> allOf(Stream<? extends CompletableFuture<?>> stream) {
    return Promise.allOf(stream);
  }

  public static CompletableFuture<Void> allWithoutContext(Stream<? extends CompletableFuture<?>> stream) {
    return CompletableFuture.allOf(toArray(stream));
  }

  public static CompletableFuture<Void> allOf(Collection<? extends CompletableFuture<?>> futures) {
    return futures.isEmpty() ? nullFuture() : Promise.allOf(futures.toArray(new CompletableFuture[0]));
  }

  public static <B> CompletableFuture<Void> afterBoth(CompletionStage<?> a, CompletionStage<B> b) {
    return a.<B, Void>thenCombine(b, (x, y) -> null).toCompletableFuture();
  }

  public static <T> ListPromise<T> allAsList(Stream<? extends CompletableFuture<? extends T>> futures) {
    return futures.collect(ListPromise.toListPromise());
  }

  public static <T, U> Promise<U> foldLeft(CompletionStage<U> identity, Stream<T> items, BiFunction<? super U, ? super T, ? extends CompletionStage<U>> combiner) {
    return foldLeft(identity, items.iterator(), combiner);
  }

  public static <T, U> Promise<U> foldLeft(CompletionStage<U> identity, Iterator<T> items, BiFunction<? super U, ? super T, ? extends CompletionStage<U>> combiner) {
    return foldLeft(identity, items, new SameThreadTrampolineExecutor(), combiner);
  }

  public static <T, U> Promise<U> foldLeft(CompletionStage<U> identity, Iterator<T> items, Executor executor, BiFunction<? super U, ? super T, ? extends CompletionStage<U>> combiner) {
    if (items.hasNext()) {
      // a naive implementation might traverse the entire stream, building a chain of futures that complete in sequence.
      // however, that may consume a lot of memory if the stream is generated dynamically, so we instead extract each
      // next() item from the iterator only when we're ready for it
      return Promise.thatCompletes(promise -> identity.whenCompleteAsync(new BiConsumer<>() {
        @Override
        public void accept(U prev, Throwable e) {
          if (e == null) {
            try {
              if (items.hasNext()) {
                combiner.apply(prev, items.next()).whenCompleteAsync(this, executor);
              } else {
                promise.complete(prev);
              }
            } catch (Exception ex) {
              promise.completeExceptionally(ex);
            }
          } else {
            promise.completeExceptionally(e);
          }
        }
      }, executor));
    } else {
      return Promise.of(identity);
    }
  }

  private static final Object SENTINEL = new Object();
  public static <T, U> CompletableFuture<U> foldLeft2(CompletionStage<U> identity, Iterator<T> items, int prefetchCount, Executor iterationExecutor, Executor combineExecutor, BiFunction<? super U, ? super T, ? extends CompletionStage<U>> combiner) {
    if (items.hasNext()) {
      Deque<CompletableFuture<T>> buffer = new ConcurrentLinkedDeque<>(List.of(CompletableFuture.supplyAsync(items::next, iterationExecutor)));
      return identity.thenCompose(new Function<U, CompletableFuture<U>>() {
        {
          for (int i = 0; i < prefetchCount; i++) {
            fetchNext();
          }
        }
        @SuppressWarnings("unchecked")
        private void fetchNext() {
          buffer.offer(buffer.getLast().thenApplyAsync(ignored -> items.hasNext() ? items.next() : (T) SENTINEL, iterationExecutor));
        }

        @Override
        public CompletableFuture<U> apply(U prev) {
          fetchNext();
          return buffer.poll().thenComposeAsync(item -> {
            if (item == SENTINEL) {
              return CompletableFuture.completedFuture(prev);
            } else {
              return combiner.apply(prev, item).thenCompose(this);
            }
          }, combineExecutor);
        }
      }).toCompletableFuture();
    } else {
      return identity.toCompletableFuture();
    }
  }

  public static <T> Promise<Void> applyInSequence(Stream<T> items, Function<? super T, ? extends CompletionStage<?>> task) {
    return foldLeft(nullFuture(), items, (ignored, item) -> task.apply(item).thenApply(ignored2 -> null));
  }

  public static <T> CompletableFuture<Collection<T>> allSuccessful(Stream<? extends CompletableFuture<? extends T>> futures) {
    return allSuccessful(toArray(futures));
  }

  @SafeVarargs
  public static <T> CompletableFuture<Collection<T>> allSuccessful(CompletableFuture<? extends T>... array) {
    return allOf(Stream.of(array).map(CompletableFutures::ignoreResult))
            .thenApply(__ -> Stream.of(array).filter(f -> !f.isCompletedExceptionally())
                    .map(CompletableFuture::join)
                    .collect(Collectors.toList())
            );
  }

  public static <T, E extends Throwable> CompletableFuture<T> recover(
          CompletionStage<T> future,
          Class<E> recoverableType,
          Function<? super E, ? extends T> recovery
  ) {
    return recover(future, recoverableType, e -> true, recovery);
  }

  public static <T, E extends Throwable> CompletableFuture<T> recover(
          CompletionStage<T> future,
          Class<E> recoverableType,
          Predicate<? super E> exceptionChecker,
          Function<? super E, ? extends T> recovery
  ) {
    return future.exceptionally(e -> applyRecovery(e, recoverableType, exceptionChecker, recovery)).toCompletableFuture();
  }

  static <T, E extends Throwable> T applyRecovery(
          Throwable e,
          Class<E> recoverableType,
          Predicate<? super E> exceptionChecker,
          Function<? super E, ? extends T> recovery
  ) {
    E asRecoverable = asRecoverable(e, recoverableType);
    if (!exceptionChecker.test(asRecoverable)) throw new CompletionException(asRecoverable);
    return recovery.apply(asRecoverable);
  }

  public static <T, E extends Throwable> OptionalPromise<T> optionalForException(CompletionStage<T> future, Class<E> recoverableType) {
    return OptionalPromise.ofFutureNullable(recover(future, recoverableType, e -> null));
  }

  public static <T> CompletableFuture<T> whenCancelled(CompletableFuture<T> future, Runnable cancellationHandler) {
    return future.whenComplete((__, e) -> {
      if (future.isCancelled()) cancellationHandler.run();
    });
  }

  public static CompletableFuture<?> ignoreResult(CompletionStage<?> future) {
    return future.handle((result, e) -> null).toCompletableFuture();
  }

  private static <E extends Throwable> E asRecoverable(Throwable e, Class<E> recoverableType) {
    Throwable cause = e;
    do {
      if (recoverableType.isInstance(cause)) return recoverableType.cast(cause);
    } while (isWrapperException(cause) && (cause = cause.getCause()) != null);
    throw e instanceof CompletionException
            ? (CompletionException) e
            : new CompletionException(e);
  }

  private static boolean isWrapperException(Throwable cause) {
    return cause instanceof CompletionException || cause instanceof ExecutionException;
  }

  public static <T, E extends Throwable> Promise<T> recoverCompose(CompletionStage<? extends T> future, Class<E> recoveryType, Function<? super E, ? extends CompletionStage<? extends T>> recovery) {
    CompletableFuture<CompletionStage<? extends T>> recovered = future.handle((v, e) -> e == null
            ? future.toCompletableFuture()
            : recovery.apply(asRecoverable(e, recoveryType))
    ).toCompletableFuture();
    return sequence(recovered);
  }

  @SuppressWarnings("unchecked")
  public static <T> Promise<T> sequence(CompletionStage<? extends CompletionStage<? extends T>> nested) {
    return Promise.of(nested).thenCompose(f -> (CompletionStage<T>) f);
  }

  @SuppressWarnings("unchecked")
  public static <T> CompletableFuture<T>[] toArray(Stream<? extends CompletableFuture<? extends T>> stream) {
    return stream.toArray(CompletableFuture[]::new);
  }

  public static Throwable unwrapExecutionException(Throwable e) {
    Throwable cause;
    if (e instanceof ExecutionException || e instanceof UncheckedExecutionException || e instanceof CompletionException || e instanceof InvocationTargetException) {
      cause = MoreObjects.firstNonNull(e.getCause(), e);
    } else {
      cause = e;
    }
    return cause;
  }

  public static <T> T getDone(CompletableFuture<T> future) {
    return getDone(future, "Future was not done");
  }

  public static <T> T getDone(CompletableFuture<T> future, String notDoneMessage) {
    checkState(future.isDone(), notDoneMessage);
    return future.join();
  }

  public static <X, T extends CompletableFuture<X>>
      Collector<T, ?, ListPromise<X>> listCollector() {
    return Collectors.collectingAndThen(Collectors.toList(), ListPromise::allAsList);
  }

}
