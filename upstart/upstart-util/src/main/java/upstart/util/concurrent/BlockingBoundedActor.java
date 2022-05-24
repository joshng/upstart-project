package upstart.util.concurrent;

import upstart.util.exceptions.FallibleSupplier;
import upstart.util.exceptions.ThrowingRunnable;
import upstart.util.exceptions.UncheckedInterruptedException;

import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

public class BlockingBoundedActor {
  private final AtomicReference<Promise<Void>> queue = new AtomicReference<>(CompletableFutures.nullFuture());
  private final Semaphore semaphore;

  public BlockingBoundedActor(int maxQueuedRequests) {
    this(new Semaphore(maxQueuedRequests));
  }

  public BlockingBoundedActor(Semaphore semaphore) {
    this.semaphore = semaphore;
  }

  public <T> Promise<T> request(FallibleSupplier<T, ?> request, Executor executor) throws UncheckedInterruptedException {
    return enqueue(prev -> prev.thenGetAsync(request, executor));
  }

  public <T> Promise<T> requestAsync(FallibleSupplier<? extends CompletionStage<T>, ?> request, Executor executor) throws UncheckedInterruptedException {
    return enqueue(prev -> prev.thenComposeGetAsync(request, executor));
  }

  public Promise<Void> send(ThrowingRunnable runnable, Executor executor) throws UncheckedInterruptedException {
    return enqueue(prev -> prev.thenRunAsync(runnable, executor));
  }

  private <T> Promise<T> enqueue(Function<Promise<Void>, Promise<T>> request) {
    try {
      semaphore.acquire();
    } catch (InterruptedException e) {
      throw UncheckedInterruptedException.propagate(e);
    }
    return Promise.<T>thatCompletes(promise -> promise.completeWith(request.apply(queue.getAndSet(promise.toVoid()))))
            .uponCompletion(semaphore::release);
  }
}
