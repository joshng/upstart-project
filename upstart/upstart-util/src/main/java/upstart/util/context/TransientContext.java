package upstart.util.context;

import upstart.util.concurrent.CompletableFutures;
import upstart.util.exceptions.Fallible;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public interface TransientContext {


  interface State extends AutoCloseable {
    @Override
    void close();

    State NULL = NullState.INSTANCE;

     enum NullState implements State {
      INSTANCE;

      public void close() {
        // nothing
      }
    }
  }

  State open();

  default TransientContext andThen(TransientContext innerContext) {
    return innerContext == NULL ? this : new CompositeTransientContext(this, innerContext);
  }

  default <T> T callInContext(Callable<T> callable) throws Exception {
    try (State ignored = open()) {
      return callable.call();
    }
  }

  default void runInContext(Runnable r) {
    try (State ignored = open()) {
      r.run();
    }
  }

  default <E extends Exception> void runOrThrowInContext(Fallible<E> r) throws E {
    try (State ignored = open()) {
      r.runOrThrow();
    }
  }

  default <T> T getInContext(Supplier<T> supplier) {
    try (State ignored = open()) {
      return supplier.get();
    }
  }

  default <I, O> O applyInContext(I input, Function<? super I, ? extends O> fn) {
    try (State ignored = open()) {
      return fn.apply(input);
    }
  }

  default <I> void acceptInContext(I input, Consumer<? super I> consumer) {
    try (State ignored = open()) {
      consumer.accept(input);
    }
  }

  default <T> CompletableFuture<T> callInContextAsync(Callable<? extends CompletionStage<T>> futureBlock) {
    final State state = open();
    try {
      return CompletableFutures.callSafely(futureBlock)
              .whenComplete((ignored, e) -> state.close());
    } catch (Exception e) {
      return CompletableFutures.failedFuture(e);
    }
  }

  default Runnable wrapRunnable(Runnable block) {
    return () -> runInContext(block);
  }

  default <O> Callable<O> wrapCallable(Callable<O> block) {
    return () -> callInContext(block);
  }

  default <I, O> Function<I, O> wrapFunction(Function<I, O> function) {
    return input -> applyInContext(input, function);
  }

  default <T> Consumer<T> wrapConsumer(Consumer<T> sink) {
    return input -> acceptInContext(input, sink);
  }

  default <T> Callable<CompletableFuture<T>> wrapAsyncCallable(Callable<? extends CompletionStage<T>> futureBlock) {
    return () -> callInContextAsync(futureBlock);
  }

  default <T, U> BiConsumer<T, U> wrapBiConsumer(BiConsumer<T, U> biConsumer) {
    return (t, u) -> runInContext(() -> biConsumer.accept(t, u));
  }

  default <A, B, C> BiFunction<A,B,C> wrapBiFunction(BiFunction<A, B, C> fn) {
    return (a, b) -> getInContext(() -> fn.apply(a, b));
  }


  TransientContext NULL = NullContext.INSTANCE;

  enum NullContext implements TransientContext {
    INSTANCE;

    @Override
    public State open() {
      return State.NULL;
    }

    @Override
    public TransientContext andThen(TransientContext innerContext) {
      return innerContext;
    }

    @Override
    public void runInContext(Runnable r) {
      r.run();
    }

    @Override
    public <T> T callInContext(Callable<T> callable) throws Exception {
      return callable.call();
    }

    @Override
    public <T> T getInContext(Supplier<T> supplier) {
      return supplier.get();
    }

    @Override
    public <I, O> O applyInContext(I input, Function<? super I, ? extends O> fn) {
      return fn.apply(input);
    }

    @Override
    public <I> void acceptInContext(I input, Consumer<? super I> consumer) {
      consumer.accept(input);
    }

    @Override
    public <T> CompletableFuture<T> callInContextAsync(Callable<? extends CompletionStage<T>> futureBlock) {
      return CompletableFutures.callSafely(futureBlock);
    }

    @Override
    public Runnable wrapRunnable(Runnable block) {
      return block;
    }

    @Override
    public <O> Callable<O> wrapCallable(Callable<O> block) {
      return block;
    }

    @Override
    public <I, O> Function<I, O> wrapFunction(Function<I, O> function) {
      return function;
    }

    @Override
    public <T> Consumer<T> wrapConsumer(Consumer<T> sink) {
      return sink;
    }

    @Override
    public <T, U> BiConsumer<T, U> wrapBiConsumer(BiConsumer<T, U> biConsumer) {
      return biConsumer;
    }

    @Override
    public <A, B, C> BiFunction<A,B,C> wrapBiFunction(BiFunction<A, B, C> fn) {
      return fn;
    }
  }
}
