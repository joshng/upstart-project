package upstart.util.concurrent;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;

public class CancellableFutureTracker<T> {
  private final PendingFutureSet<T> pendingFutures = new PendingFutureSet<>();
  private final boolean interruptOnCancel;
  private volatile boolean isCancelled = false;

  public CancellableFutureTracker() {
    this(false);
  }

  public CancellableFutureTracker(boolean interruptOnCancel) {
    this.interruptOnCancel = interruptOnCancel;
  }

  @SuppressWarnings("unchecked")
  public <F extends CompletableFuture<T>> F propagateCancellationFrom(F future) {
    return (F) CompletableFutures.whenCancelled(future, this::cancelPendingFutures);
  }

  public CompletableFuture<T> callUnlessCancelled(Callable<? extends CompletableFuture<T>> asyncTask) {
    if (isCancelled) return CompletableFutures.cancelledFuture();
    return track(CompletableFutures.callSafely(asyncTask));
  }

  public <F extends CompletableFuture<T>> F track(F future) {
    if (checkCancellation(future) && pendingFutures.isPending(future)) {
      checkCancellation(future); // check again after adding in pendingFutures, to avoid a race
    }
    return future;
  }

  public void cancelPendingFutures() {
    isCancelled = true;
    pendingFutures.pendingFutures().forEach(this::cancelFuture);
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
