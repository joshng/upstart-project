package upstart.util.concurrent;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RunnableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

public class CompletableFutureTask<T> extends Promise<T> implements RunnableFuture<T> {
  private final AtomicBoolean called = new AtomicBoolean();
  private final Callable<T> callable;
  private Thread completingThread = null;

  public CompletableFutureTask(Callable<T> callable) {
    this.callable = callable;
  }

  public Promise<T> ensureStarted() {
    run();
    return this;
  }

  @Override
  public void run() {
    if (called.compareAndSet(false, true)) {
      Thread currentThread = Thread.currentThread();
      synchronized (called) {
        if (!isCancelled()) {
          completingThread = currentThread;
        } else {
          return;
        }
      }

      tryComplete(callable);

      synchronized (called) {
        if (isCancelled()) Thread.interrupted();
        completingThread = null;
      }
    }
  }

  /**
   * Note that invoking cancel(true) on this CompletableFutureTask directly may attempt to interrupt a
   * concurrently-completing thread, but canceling a CompletableFuture that is <em>derived</em> from this task will
   * likely <strong>not</strong> do so.
   *
   * @see CompletableFuture#cancel
   */
  @Override
  public boolean cancel(boolean mayInterruptIfRunning) {
    boolean cancelled = super.cancel(mayInterruptIfRunning);
    if (cancelled && mayInterruptIfRunning) {
      synchronized (called) {
        Thread thread = completingThread;
        if (thread != null) thread.interrupt();
      }
    }
    return cancelled;
  }
}
