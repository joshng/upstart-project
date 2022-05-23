package upstart.util.concurrent.resourceguard;

import upstart.util.concurrent.Deadline;
import upstart.util.concurrent.ShutdownException;
import upstart.util.concurrent.services.LightweightService;

import static com.google.common.base.Preconditions.checkArgument;

public class SemaphoreResourceGuard extends LightweightService implements BoundedResourceGuard<SemaphoreResourceGuard> {
  private final Object syncRoot = new Object();
  private boolean aThreadHasPriority = false;
  private volatile int availablePermits;

  public SemaphoreResourceGuard(int availablePermits) {
    this.availablePermits = availablePermits;
  }

  public int availablePermits() {
    return availablePermits;
  }

  @Override
  protected void startUp() throws Exception {
  }

  @Override
  protected void shutDown() throws Exception {
    synchronized (syncRoot) {
      syncRoot.notifyAll();
    }
  }

  @Override
  public boolean tryAcquire(int permits) {
    checkPermits(permits);

    synchronized (syncRoot) {
      boolean acquired = !aThreadHasPriority && availablePermits >= permits;
      if (acquired) availablePermits -= permits;
      return acquired;
    }
  }

  @Override
  public boolean tryAcquire(int permits, Deadline deadline) throws InterruptedException, ShutdownException {
    checkPermits(permits);

    synchronized (syncRoot) {
      while (aThreadHasPriority) {
        if (!awaitAvailableOrClosed(deadline)) return false;
      }
      aThreadHasPriority = true;
      try {
        while (availablePermits <= permits) {
          if (!awaitAvailableOrClosed(deadline)) return false;
        }
        availablePermits -= permits;
        return true;
      } finally {
        aThreadHasPriority = false;
      }
    }
  }

  @Override
  public void acquire(int permits) throws InterruptedException, ShutdownException {
    var acquired = tryAcquire(permits, Deadline.NONE);
    assert acquired : "failed to acquire with unbounded deadline";
  }

  private void checkPermits(int permits) {
    throwIfShutDown();
    checkArgument(permits > 0, "Requested permits (%s) must be positive", permits);
  }

  @Override
  public void release(int permits) {
    synchronized (syncRoot) {
      availablePermits += permits;
      syncRoot.notifyAll();
    }
  }

  private boolean awaitAvailableOrClosed(Deadline deadline) throws InterruptedException {
    throwIfShutDown();
    if (deadline.isExpired()) return false;
    deadline.wait(syncRoot);
    return true;
  }

}
