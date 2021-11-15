package upstart.util.context;

import com.google.common.base.Throwables;
import com.google.common.collect.Lists;
import upstart.util.concurrent.CompletableFutures;
import upstart.util.exceptions.Exceptions;
import upstart.util.exceptions.Fallible;
import upstart.util.exceptions.FallibleSupplier;
import upstart.util.exceptions.ThrowingRunnable;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public interface TransientContext {
  static CompositeTransientContext sequence(TransientContext first, TransientContext second, TransientContext... rest) {
    return new CompositeTransientContext(Lists.asList(first, second, rest));
  }

  interface State {
    void exit();

    State NULL = NullState.INSTANCE;

    public enum NullState implements State {
      INSTANCE;

      public void exit() {
        // nothing
      }
    }
  }

  State enter();

  default TransientContext andThen(TransientContext innerContext) {
    return innerContext != NullContext.INSTANCE ? sequence(this, innerContext) : this;
  }

  default void runInContext(Runnable r) {
    try {
      callInContext(Fallible.fallible(r::run));
    } catch (Exception e) {
      throw Exceptions.throwUnchecked(e);
    }
  }

  default <T> T callInContext(Callable<T> callable) throws Exception {
    State state = enter();
    try {
      return callable.call();
    } finally {
      state.exit();
    }
  }

  default <T> T getInContext(Supplier<T> supplier) {
    try {
      return callInContext(supplier::get);
    } catch (Exception e) {
      throw Exceptions.throwUnchecked(e);
    }
  }

  default <I, O> O applyInContext(I input, Function<? super I, ? extends O> fn) {
    try {
      return callInContext(() -> fn.apply(input));
    } catch (Exception e) {
      throw Exceptions.throwUnchecked(e);
    }
  }

  default <I> void acceptInContext(I input, Consumer<? super I> consumer) {
    try {
      callInContext(() -> {
        consumer.accept(input);
        return null;
      });
    } catch (Exception e) {
      throw Throwables.propagate(e);
    }
  }

  default <T> CompletableFuture<T> callInContextAsync(Callable<? extends CompletionStage<T>> futureBlock) {
    final State state = enter();
    try {
      return CompletableFutures.callSafely(futureBlock)
              .whenComplete((ignored, e) -> state.exit());
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

  TransientContext NULL = NullContext.INSTANCE;

  enum NullContext implements TransientContext {
    INSTANCE;

    @Override
    public State enter() {
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
  }
}
