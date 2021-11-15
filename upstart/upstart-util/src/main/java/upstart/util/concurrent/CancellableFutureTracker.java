package upstart.util.concurrent;

import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

public class CancellableFutureTracker {
  private final Set<CompletableFuture<?>> pendingFutures = ConcurrentHashMap.newKeySet();
  private final boolean interruptOnCancel;
  private volatile boolean isCancelled = false;

  public CancellableFutureTracker() {
    this(false);
  }

  public CancellableFutureTracker(boolean interruptOnCancel) {
    this.interruptOnCancel = interruptOnCancel;
  }

  @SuppressWarnings("unchecked")
  public <T, F extends CompletableFuture<T>> F propagateCancellationFrom(F future) {
    return (F) CompletableFutures.whenCancelled(future, this::cancelPendingFutures);
  }

  public <T> CompletableFuture<T> callUnlessCancelled(Callable<? extends CompletableFuture<T>> asyncTask) {
    if (isCancelled) return CompletableFutures.cancelledFuture();
    return track(CompletableFutures.callSafely(asyncTask));
  }

  public <F extends CompletionStage<?>> F track(F f) {
    CompletableFuture<?> future = f.toCompletableFuture();
    if (!future.isDone() && checkCancellation(future)) {
      pendingFutures.add(future);
      checkCancellation(future); // check again after adding in pendingFutures, to avoid a race
      future.whenComplete((ignored, e) -> pendingFutures.remove(future));
    }
    return f;
  }

  public void cancelPendingFutures() {
    isCancelled = true;
    pendingFutures.forEach(this::cancelFuture);
  }

  public boolean isCancelled() {
    return isCancelled;
  }

  private boolean checkCancellation(CompletableFuture<?> future) {
    boolean cancel = isCancelled;
    if (cancel) cancelFuture(future);
    return !cancel;
  }

  private void cancelFuture(CompletableFuture<?> future) {
    future.cancel(interruptOnCancel);
  }
}
