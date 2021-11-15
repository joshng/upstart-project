package upstart.util.concurrent;

import upstart.util.exceptions.ThrowingRunnable;
import upstart.util.exceptions.UncheckedInterruptedException;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicReference;

public class BlockingBoundedActor {
  private final AtomicReference<CompletionStage<Void>> queue = new AtomicReference<>(CompletableFutures.nullFuture());
  private final Semaphore semaphore;

  public BlockingBoundedActor(int maxQueuedRequests) {
    this(new Semaphore(maxQueuedRequests));
  }

  public BlockingBoundedActor(Semaphore semaphore) {
    this.semaphore = semaphore;
  }

  public <T> CompletableFuture<T> request(Callable<T> request, Executor executor) throws UncheckedInterruptedException {
    try {
      semaphore.acquire();
    } catch (InterruptedException e) {
      throw UncheckedInterruptedException.propagate(e);
    }
    return Promise.<T>thatCompletes(promise -> queue.getAndSet(promise.toVoid()).thenRunAsync(() -> promise.tryComplete(request), executor))
            .uponCompletion(semaphore::release);
  }

  public <T> CompletableFuture<T> requestAsync(Callable<? extends CompletionStage<T>> request, Executor executor) throws UncheckedInterruptedException {
    try {
      semaphore.acquire();
    } catch (InterruptedException e) {
      throw UncheckedInterruptedException.propagate(e);
    }
    return Promise.<T>thatCompletes(promise -> queue.getAndSet(promise.toVoid()).thenRunAsync(() -> promise.tryCompleteWith(request), executor))
            .uponCompletion(semaphore::release);
  }

  public CompletableFuture<Void> send(ThrowingRunnable runnable, Executor executor) throws UncheckedInterruptedException {
    try {
      semaphore.acquire();
    } catch (InterruptedException e) {
      throw UncheckedInterruptedException.propagate(e);
    }

    return Promise.<Void>thatCompletes(promise -> queue.getAndSet(promise).thenRunAsync(() -> promise.tryComplete(runnable), executor))
            .uponCompletion(semaphore::release);
  }
}
