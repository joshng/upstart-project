package upstart.test;

import upstart.util.collect.MoreStreams;
import upstart.util.concurrent.Promise;

import java.time.Duration;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

public class ThreadPauseHelper {
  private final Queue<PendingPause> pendingPauses = new ConcurrentLinkedQueue<>();

  public OngoingPause requestAndAwaitPause(int threadsToPause, Duration initiationTimeout) throws InterruptedException, TimeoutException {
    return requestPause(threadsToPause).awaitPause(initiationTimeout);
  }

  public PendingPause requestPause(int threadsToPause) {
    PendingPause pause = new PendingPause(threadsToPause);
    pendingPauses.offer(pause);
    return pause;
  }

  public CompletableFuture<Void> doWhenPaused(int threadsToPause, Runnable action) {
    return requestPause(threadsToPause).doWhenPaused(true, action);
  }

  public CompletableFuture<Void> runDuringSequentialPauses(int threadsToPause, Runnable... actions) {
    return MoreStreams.foldLeft(
            CompletableFuture.completedFuture(requestPause(threadsToPause)),
            Stream.of(actions),
            (pauseFuture, action) -> pauseFuture
                    .thenCompose(pause -> pause.doWhenPaused(false, action)
                            .thenApply(ignored -> pause.resumeOnce(threadsToPause))
                    )
    ).thenAccept(PendingPause::resume);
  }

  public void pauseIfRequested(Duration completionTimeout) throws InterruptedException, TimeoutException {
    while (true) {
      PendingPause pause = pendingPauses.peek();
      if (pause == null || pause.onPaused(completionTimeout)) return;
      pendingPauses.remove(pause);
    }
  }

  public class PendingPause implements AutoCloseable {
    private final AtomicInteger countdown;
    private final Promise<Void> pausedPromise = new Promise<>();
    private final Promise<Void> resumedPromise = new Promise<>();
    private PendingPause(int threadsToPause) {
      countdown = new AtomicInteger(threadsToPause);
    }

    public OngoingPause awaitPause(Duration initiationTimeout) throws InterruptedException, TimeoutException {
      try {
        pausedPromise.get(initiationTimeout.toNanos(), TimeUnit.NANOSECONDS);
      } catch (ExecutionException e) {
        throw new RuntimeException(e);
      }
      return new OngoingPause(this);
    }

    public CompletableFuture<Void> doWhenPaused(boolean resumeAfter, Runnable action) {
      return pausedPromise.thenRun(() -> {
        try {
          action.run();
        } catch (Throwable e) {
          resumedPromise.completeExceptionally(e);
        } finally {
          if (resumeAfter) resume();
        }
      });
    }

    public void resume() {
      resumedPromise.complete(null);
    }

    public PendingPause resumeOnce(int threadsToPause) {
      PendingPause nextPause = requestPause(threadsToPause);
      resume();
      return nextPause;
    }

    @Override
    public void close() throws Exception {
      resume();
    }

    private boolean onPaused(Duration initiationTimeout) throws InterruptedException, TimeoutException {
      int count = countdown.decrementAndGet();
      if (count < 0) return false;
      if (count == 0) pausedPromise.complete(null);
      try {
        resumedPromise.get(initiationTimeout.toNanos(), TimeUnit.NANOSECONDS);
      } catch (InterruptedException | TimeoutException e) {
        pausedPromise.obtrudeException(e);
        throw e;
      } catch (ExecutionException e) {
        throw new RuntimeException(e);
      }
      return true;
    }
  }

  public class OngoingPause implements AutoCloseable {
    private final PendingPause pendingPause;

    private OngoingPause(PendingPause pendingPause) {
      this.pendingPause = pendingPause;
    }

    public void resume() {
      pendingPause.resume();
    }

    public PendingPause resumeOnce(int threadsToPause) {
      return pendingPause.resumeOnce(threadsToPause);
    }

    @Override
    public void close() {
      resume();
    }
  }
}
