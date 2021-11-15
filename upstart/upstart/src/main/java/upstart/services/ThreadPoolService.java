package upstart.services;

import upstart.util.concurrent.CompletableFutures;
import upstart.util.exceptions.Exceptions;
import upstart.util.exceptions.Fallible;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public abstract class ThreadPoolService extends IdleService implements Executor {
  private final Duration shutdownGracePeriod;
  private ExecutorService executorService;

  protected ThreadPoolService(Duration shutdownGracePeriod) {
    this.shutdownGracePeriod = shutdownGracePeriod;
  }

  public static ThreadPoolService from(Duration shutdownGracePeriod, Supplier<ExecutorService> executorServiceSupplier) {
    return new ThreadPoolService(shutdownGracePeriod) {
      @Override
      protected ExecutorService buildExecutorService() {
        return executorServiceSupplier.get();
      }
    };
  }

  protected abstract ExecutorService buildExecutorService();

  public void execute(Runnable command) {
    executorService.execute(command);
  }

  protected  <T> CompletableFuture<T> supplyAsync(Supplier<T> supplier) {
    return CompletableFuture.supplyAsync(supplier, executorService);
  }

  protected  <T> CompletableFuture<T> callAsync(Callable<T> supplier) {
    return CompletableFuture.supplyAsync(() -> {
      try {
        return supplier.call();
      } catch (Exception e) {
        throw Exceptions.throwUnchecked(e);
      }
    }, executorService);
  }

  protected CompletableFuture<Void> runAsync(Fallible<?> runnable) {
    return CompletableFuture.runAsync(runnable, executorService);
  }

  protected <T> CompletableFuture<T> composeAsync(Callable<? extends CompletableFuture<T>> asyncSupplier) {
    return CompletableFutures.sequence(callAsync(asyncSupplier));
  }

  @Override
  protected boolean startUpOnSeparateThread() {
    return false;
  }

  @Override
  protected void startUp() throws Exception {
    executorService = buildExecutorService();
  }

  @Override
  protected void shutDown() throws Exception {
    executorService.shutdown();
    if (!executorService.awaitTermination(shutdownGracePeriod.toMillis(), TimeUnit.MILLISECONDS)) {
      LoggerFactory.getLogger(getClass().getPackage().getName() + "." + serviceName()).error("ThreadPoolService didn't shut down within {}ms, interrupting\n{}", shutdownGracePeriod.toMillis(), executorService);
      executorService.shutdownNow();
    }
  }
}
