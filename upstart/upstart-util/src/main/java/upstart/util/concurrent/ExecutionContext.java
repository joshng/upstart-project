package upstart.util.concurrent;

import com.google.common.util.concurrent.ForwardingExecutorService;
import upstart.util.exceptions.Exceptions;
import upstart.util.exceptions.Fallible;
import upstart.util.functions.AsyncFunction;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public interface ExecutionContext extends Executor {
  static ExecutionContext of(Executor executor) {
    return executor instanceof ExecutionContext ec ? ec : executor::execute;
  }

  static ExecutorServiceContext of(ExecutorService executor) {
    return executor instanceof ExecutorServiceContext ec ? ec : new ForwardingExecutorServiceContext(executor);
  }

  interface ExecutorServiceContext extends ExecutionContext, ExecutorService {
  }

  class ForwardingExecutorServiceContext extends ForwardingExecutorService implements ExecutorServiceContext {
    private final ExecutorService delegate;

    public ForwardingExecutorServiceContext(ExecutorService delegate) {
      this.delegate = delegate;
    }

    @Override
    protected ExecutorService delegate() {
      return delegate;
    }
  }

  default <T> CompletableFuture<T> supplyAsync(Supplier<T> supplier) {
    return CompletableFuture.supplyAsync(supplier, this);
  }

  default <T> CompletableFuture<T> callAsync(Callable<T> supplier) {
    return CompletableFuture.supplyAsync(() -> {
      try {
        return supplier.call();
      } catch (Exception e) {
        throw Exceptions.throwUnchecked(e);
      }
    }, this);
  }

  default CompletableFuture<Void> runAsync(Fallible<?> runnable) {
    return CompletableFuture.runAsync(runnable, this);
  }

  default <T> Promise<T> composeAsync(Callable<? extends CompletableFuture<T>> asyncSupplier) {
    return CompletableFutures.sequence(callAsync(asyncSupplier));
  }

  default <I, O> AsyncFunction<I, O> wrapFunction(Function<I, O> function) {
    return input -> supplyAsync(() -> function.apply(input));
  }

  default <T> AsyncFunction<T, Void> wrapConsumer(Consumer<T> consumer) {
    return input -> runAsync(() -> consumer.accept(input));
  }

  default <T> Supplier<CompletableFuture<T>> wrapCallable(Callable<T> callable) {
    return () -> callAsync(callable);
  }
}
