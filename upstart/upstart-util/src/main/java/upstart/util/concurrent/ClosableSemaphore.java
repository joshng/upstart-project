package upstart.util.concurrent;

import static com.google.common.base.Preconditions.checkArgument;

public class ClosableSemaphore implements AutoCloseable {
  private final Object lock = new Object();
  private boolean aThreadHasPriority = false;
  private int availablePermits;
  private volatile boolean closed = false;


  public ClosableSemaphore(int availablePermits) {
    this.availablePermits = availablePermits;
  }
  
  public void acquire() throws InterruptedException, ShutdownException {
    acquire(1);
  }
  
  public void acquire(int permits) throws InterruptedException, ShutdownException {
    if (permits == 0) return;
    checkArgument(permits > 0, "Negative permits", permits);

    synchronized (lock) {
      while (aThreadHasPriority) {
        awaitAvailableOrClosed();
      }
      aThreadHasPriority = true;
      try {
        while (availablePermits <= permits) {
          awaitAvailableOrClosed();
        }
        availablePermits -= permits;
      } finally {
        aThreadHasPriority = false;
      }
    }
  }

  public void release() {
    release(1);
  }

  public void release(int permits) {
    synchronized (lock) {
      availablePermits += permits;
      lock.notifyAll();
    }
  }

  private void awaitAvailableOrClosed() throws InterruptedException {
    checkClosed();
    lock.wait();
  }

  protected void checkClosed() {
    ShutdownException.throwIf(closed);
  }

  public boolean isClosed() {
    return closed;
  }

  @Override
  public void close() {
    synchronized (lock) {
      if (!closed) {
        closed = true;
        lock.notifyAll();
      }
    }
  }
}
