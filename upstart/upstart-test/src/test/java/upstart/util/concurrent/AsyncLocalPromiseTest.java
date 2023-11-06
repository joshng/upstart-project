package upstart.util.concurrent;

import com.google.common.base.Stopwatch;
import com.google.common.truth.OptionalSubject;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.pcollections.HashTreePSet;
import org.pcollections.PSet;
import upstart.test.ThreadPauseHelper;
import upstart.test.truth.MoreTruth;
import upstart.util.context.AsyncContext;
import upstart.util.context.AsyncContextManager;
import upstart.util.context.AsyncLocal;
import upstart.util.exceptions.FallibleSupplier;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

import static com.google.common.truth.OptionalSubject.optionals;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static upstart.test.truth.CompletableFutureSubject.assertThat;
import static upstart.util.exceptions.FallibleFunction.fallibleFunction;

class AsyncLocalPromiseTest {

  static ExecutorService executor;
  @BeforeAll
  static void beforeAll() {
    executor = Executors.newCachedThreadPool();
  }

  @AfterAll
  static void afterAll() {
    executor.shutdownNow();
  }

  @Test
  void exerciseAsyncLocalMerge() throws InterruptedException, TimeoutException {
    AsyncLocal<String> state1 = AsyncLocal.newAsyncLocal("state1");
    AsyncLocal<String> state2 = AsyncLocal.newAsyncLocal("state2");
    AsyncLocal<String> state3 = AsyncLocal.newAsyncLocal("state3");

    assertThat(state1.get()).isNull();
    assertThat(state2.get()).isNull();

    try (var ignored = AsyncContext.emptyContext().open()) {

      Deadline deadline = Deadline.withinSeconds(5);
      ThreadPauseHelper pauseHelper = new ThreadPauseHelper(deadline);
      ThreadPauseHelper.PendingPause pendingPause = pauseHelper.requestPause(1);


      state1.set("foo");

      Promise<String> promise1 = Promise.callAsync(() -> {
        pauseHelper.pauseIfRequested();

        state2.set("bar");

        return state1.get();
      }, executor);

      var finalPromise = promise1
              .thenApplyAsync(foo -> foo + state2.get(), executor) // 3
              .thenApply(fallibleFunction(foobar -> { // 4
                pauseHelper.pauseIfRequested();
                assertThat(state1.get()).isEqualTo("foo");
                state1.set("butterfly");
                state2.set("oops");
                state3.set("bubblegum");
                return foobar;
              })).thenApplyAsync(foobar -> { // 5
                return foobar + state1.get(); // foobarbutterfly
              }, executor);

      CompletableFuture<Void> pause1 = pendingPause.doWhenPaused(false, () -> state1.set("botched"));
      CompletableFuture<Void> pause2 = pauseHelper.requestPause(1).doWhenPaused(
              true,
              () -> assertThat(state2.get()).isEqualTo("bar")
      );
      pendingPause.resume();
      pause1.join();
      pause2.join();

      promise1.thenRun(() -> assertThat(state2.get()).isEqualTo("bar")).join();
      finalPromise.thenRun(() -> assertThat(state2.get()).isEqualTo("oops")).join();

      assertThat(finalPromise.thenApplyAsync(foobarbutterfly -> foobarbutterfly + state3.get(), executor))
              .doneWithin(deadline)
              .havingResultThat().isEqualTo("foobarbutterflybubblegum");

      assertThat(state1.get()).isEqualTo("foo");
      assertThat(state1.getFromCompletion(finalPromise).join().orElseThrow()).isEqualTo("butterfly");
      assertThat(state2.getFromCompletion(finalPromise).join().orElseThrow()).isEqualTo("oops");
      assertThat(state2.get()).isNull();
    }

    assertThat(state1.get()).isNull();
    assertThat(state2.get()).isNull();
  }

  @Test
  void aggregateContextsMerge() throws InterruptedException {
    var deadline = Deadline.withinSeconds(500);
    for (int i = 0; i < 10000; i++) {
      AsyncContext.emptyContext().runOrThrowInContext(() -> {
        AsyncLocal<PSet<String>> state = AsyncLocal.newAsyncLocal("strs", HashTreePSet.empty(), PSet::plusAll);
        List<String> expectedValues = List.of("a", "b", "c", "d", "e", "f", "g");
        ListPromise<PSet<String>> setsPromise = expectedValues.stream()
                .map(x -> Promise.callAsync(() -> state.updateAndGet(list -> list.plus(x)), executor))
                .collect(ListPromise.toListPromise());
        ListPromise<String> strs = setsPromise.thenStreamToList(sets -> sets.stream().flatMap(Set::stream));
        assertThat(strs).doneWithin(deadline).havingResultThat(MoreTruth.iterables())
                .containsExactlyElementsIn(expectedValues);
      });
    }
  }

  @Test
  void asyncContextIsRetained() {
    AsyncLocal<String> state1 = AsyncLocal.newAsyncLocal("state1");
    state1.set("bar");
    Promise<String> p1 = new Promise<>();
    var p2 = p1.thenApply(foo -> foo + state1.get());
    p1.complete("foo");
    assertThat(p1).havingResultThat().isEqualTo("foo");
    assertThat(p2).havingResultThat().isEqualTo("foobar");
  }

  @Test
  void failuresRetainContext() throws InterruptedException {
    AsyncLocal<String> state1 = AsyncLocal.newAsyncLocal("state1");
    state1.set("bar");
    Promise<String> p1 = new Promise<>();
    var p2 = p1.thenApplyAsync(foo -> {
      state1.set("baz");
      throw new ArithmeticException("oops");
    }, executor);
    p1.complete("foo");
    assertThat(p1).havingResultThat().isEqualTo("foo");
    assertThat(state1.getFromCompletion(p1)).havingResultThat(optionals()).hasValue("bar");
    assertThat(p2).doneWithin(Deadline.withinSeconds(5)).completedExceptionallyWith(ArithmeticException.class);
    assertThat(state1.getFromCompletion(p2)).havingResultThat(optionals()).hasValue("baz");
//    assertThat(state1.get()).isEqualTo("baz");
  }


  @Test
  void retainedAcrossComposition() throws InterruptedException {
    AsyncLocal<String> state1 = AsyncLocal.newAsyncLocal("state1");
    AsyncLocal<String> state2 = AsyncLocal.newAsyncLocal("state2");
    state1.set("a");
    Promise<String> p1 = new Promise<>();
    var p2 = p1.thenComposeAsync(foo -> {
      state1.updateAndGet(a -> a + "b");
      state2.set("x");
      return Promise.callAsync(() -> state1.updateAndGet(ab -> ab + "c"), executor);
    });

    state2.set("y");
    p1.complete("foo");
    assertThat(p2).doneWithin(Deadline.withinSeconds(5)).havingResultThat().isEqualTo("abc");
    assertThat(state2.getFromCompletion(p2)).havingResultThat(optionals()).hasValue("x");
  }

  @Test
  void retainedAcrossFailedComposition() throws InterruptedException {
    AsyncLocal<String> state1 = AsyncLocal.newAsyncLocal("state1");
    AsyncLocal<String> state2 = AsyncLocal.newAsyncLocal("state2");
    state1.set("a");
    Promise<String> p1 = new Promise<>();
    var p2 = p1.thenComposeAsync(foo -> {
      state1.updateAndGet(a -> a + "b");
      state2.set("x");
      return Promise.callAsync(() -> {
        state1.updateAndGet(ab -> ab + "c");
        throw new ArithmeticException();
      }, executor);
    });

    state2.set("y");
    p1.complete("foo");
    assertThat(p2).doneWithin(Deadline.withinSeconds(5)).completedExceptionallyWith(ArithmeticException.class);
    assertThat(state1.getFromCompletion(p2)).havingResultThat(optionals()).hasValue("abc");
    assertThat(state2.getFromCompletion(p2)).havingResultThat(optionals()).hasValue("x");

  }

  @Test
  void mapCompose() throws InterruptedException {
    Promise<String> p1 = new Promise<>();
    OptionalPromise<String> p2 = p1.thenApplyOptional(Optional::ofNullable);
    OptionalPromise<String> result = p2.thenMapCompose(v -> CompletableFuture.completedFuture(v + "bar"));
    p1.complete("foo");
    assertThat(result).doneWithin(Deadline.withinSeconds(5)).havingResultThat(OptionalSubject.optionals()).hasValue("foobar");
  }

  @Test
  void flatMapCompose() throws InterruptedException {
    Promise<String> p1 = new Promise<>();
    OptionalPromise<String> p2 = p1.thenApplyOptional(Optional::ofNullable);
    OptionalPromise<String> result = p2.thenFlatMapCompose(v -> Promise.of(CompletableFuture.completedFuture(Optional.of(v + "bar"))));
    p1.complete("foo");
    assertThat(result).doneWithin(Deadline.withinSeconds(5)).havingResultThat(OptionalSubject.optionals()).hasValue("foobar");
  }

  @Test
  void flatMapComposeWith() throws InterruptedException {
    Promise<String> p1 = new Promise<>();
    OptionalPromise<String> p2 = p1.thenApplyOptional(Optional::ofNullable);
    OptionalPromise<String> result = p2.thenFlatMapComposeWith(p1, p1, (v, foo, foo2) -> Promise.of(CompletableFuture.completedFuture(Optional.of(v + foo + "bar"))));
    p1.complete("foo");
    assertThat(result).doneWithin(Deadline.withinSeconds(5)).havingResultThat(OptionalSubject.optionals()).hasValue("foofoobar");
  }

  @Test
  void emptyFlatMapComposeWith() throws InterruptedException {
    Promise<String> p1 = new Promise<>();
    OptionalPromise<String> p2 = new OptionalPromise<>();
    OptionalPromise<String> result = p2.thenFlatMapComposeWith(p1, p1, (v, foo, foo2) -> Promise.of(CompletableFuture.completedFuture(Optional.of(v + foo + "bar"))));
    p1.complete("foo");
    p2.complete(Optional.empty());
    assertThat(result).doneWithin(Deadline.withinSeconds(5)).havingResultThat(OptionalSubject.optionals()).isEmpty();
  }

  @Test
  void wrappedPromiseRetainsContext() throws InterruptedException {
    AsyncLocal<String> state = AsyncLocal.newAsyncLocal("state");
    state.set("bar");
    CountDownLatch latch = new CountDownLatch(1);
    Promise<String> p1 = Promise.of(CompletableFuture.supplyAsync(
            FallibleSupplier.of(() -> {
              latch.await();
              return "foo";
            }
    ))).thenApply(foo -> foo + state.get());
    AsyncContext.clear();
    latch.countDown();
    assertThat(p1)
            .doneWithin(Deadline.withinSeconds(20000))
            .havingResultThat()
            .isEqualTo("foobar");
  }

  static ThreadLocalReference<Integer> threadValue = new ThreadLocalReference<>();
  static AsyncLocal<Integer> asyncValue = AsyncLocal.newAsyncLocal("test");

  @Disabled
  @Test
  void perfIsAcceptable() {
    var bgThread = Executors.newSingleThreadExecutor();
    Function<Integer, Integer> promiseContinuation = n -> {
      var result = n + 1;
      asyncValue.set(result);
      return result;
    };
    Function<Integer, Integer> cfContinuation = n -> {
      var result = n + 1;
      threadValue.set(result);
      return result;
    };
    int reps = 5000;
    benchmark(reps, promiseContinuation, cfContinuation, bgThread);
  }

  private void benchmark(
          int reps,
          Function<Integer, Integer> promiseContinuation,
          Function<Integer, Integer> cfContinuation,
          ExecutorService bgThread
  ) {
    long bestPromise = Long.MAX_VALUE;
    long bestCf = Long.MAX_VALUE;
    int chainLength = 50;
    for (int iterations = 0; iterations < 500; iterations++) {
      var stopwatch = Stopwatch.createStarted();
      Promise<Integer> p = new Promise<>(), firstPromise = p;

      int startIdx = 0;
      for (int i = 0; i < reps; i++) {
        if (i % 4 == 0) {
          p = p.thenApplyAsync(promiseContinuation, bgThread);
        } else {
          p = p.thenApply(promiseContinuation);
        }
        if ((i+1) % chainLength == 0) {
          firstPromise.complete(startIdx);
          assertThat(p.join()).isEqualTo(startIdx + chainLength);
          p = firstPromise = new Promise<>();
          startIdx = i;
        }
      }

      firstPromise.complete(1);
      p.join();

      bestPromise = Math.min(bestPromise, stopwatch.elapsed(TimeUnit.NANOSECONDS));
      assertThat(asyncValue.get()).isEqualTo(reps-1);

      stopwatch = Stopwatch.createStarted();
      CompletableFuture<Integer> cf = new CompletableFuture<>(), firstCf = cf;

      startIdx = 0;
      for (int i = 0; i < reps; i++) {
        if (i % 4 == 0) {
          cf = cf.thenApplyAsync(cfContinuation, bgThread);
        } else {
          cf = cf.thenApply(cfContinuation);
        }
        if ((i+1) % chainLength == 0) {
          firstCf.complete(startIdx);
          assertThat(cf.join()).isEqualTo(startIdx + chainLength);
          cf = firstCf = new CompletableFuture<>();
          startIdx = i;
        }
      }

      firstCf.complete(1);
      cf.join();
      bestCf = Math.min(bestCf, stopwatch.elapsed(TimeUnit.NANOSECONDS));
    }

    double slowdown = ((double) bestPromise) / bestCf;

    // empirically, this ratio is about 1.6x, but we'll pad heavily here to avoid spurious failures on heavily-loaded machines
    assertWithMessage("overhead/slowdown due to AsyncLocal").that(slowdown).isLessThan(20);
//    long nanosPerMilli = TimeUnit.MILLISECONDS.toNanos(1);
//    System.out.printf("per Promise: %fms%n", ((double)bestPromise) / (reps * nanosPerMilli));
//    System.out.printf("per CompFuture: %fms%n", ((double)bestCf) / (reps * nanosPerMilli));
//    System.out.printf("xN: %f%n", slowdown);
  }

  @Nested
  class WithCustomContextManager {
    final ThreadLocalReference<String> threadLocal = new ThreadLocalReference<>();

    private final AsyncContextManager<String> contextManager = new AsyncContextManager<>() {
      @Override
      public Optional<String> captureSnapshot() {
        return threadLocal.getOptional();
      }

      @Override
      public void restoreSnapshot(String value) {
        threadLocal.set(value);
      }

      @Override
      public void mergeApplyFromSnapshot(String value) {
        threadLocal.set(value);
      }

      @Override
      public void remove() {
        threadLocal.remove();
      }

      @Override
      public String mergeSnapshots(String mergeTo, String mergeFrom) {
        return mergeFrom;
      }
    };

    @BeforeEach
    void setUp() {
      threadLocal.remove();

      AsyncContext.registerContextManager(contextManager);
    }

    @AfterEach
    void tearDown() {
      AsyncContext.unregisterContextManager(contextManager);
    }

    @Test
    void customContextManager() throws InterruptedException {
      threadLocal.set("start");
      assertThat(Promise.callAsync(() -> threadLocal.updateAndGet(s -> s + " middle"), executor)
                         .thenApplyAsync(s -> threadLocal.updateAndGet(s2 -> s2 + " end"), executor)
      ).doneWithin(Deadline.withinSeconds(5))
              .havingResultThat().isEqualTo("start middle end");

      assertThat(threadLocal.get()).isEqualTo("start");
    }
  }
}