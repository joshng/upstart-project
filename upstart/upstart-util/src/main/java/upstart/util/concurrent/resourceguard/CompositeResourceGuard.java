package upstart.util.concurrent.resourceguard;

import upstart.util.concurrent.Deadline;
import upstart.util.concurrent.ShutdownException;
import upstart.util.concurrent.services.AggregateService;
import upstart.util.concurrent.services.ComposableService;

import java.util.Arrays;

public class CompositeResourceGuard<A extends BoundedResourceGuard<A>, B extends BoundedResourceGuard<B>> extends AggregateService implements BoundedResourceGuard<CompositeResourceGuard<A, B>> {
  private final A firstGuard;
  private final B secondGuard;

  public CompositeResourceGuard(A firstGuard, B secondGuard) {
    this.firstGuard = firstGuard;
    this.secondGuard = secondGuard;
  }

  public A firstGuard() {
    return firstGuard;
  }

  public B secondGuard() {
    return secondGuard;
  }

  @Override
  public boolean tryAcquire(int permits) {
    if (!firstGuard.tryAcquire(permits)) return false;
    boolean acquired = secondGuard.tryAcquire(permits);
    if (!acquired) firstGuard.release(permits);
    return acquired;
  }

  @Override
  public boolean tryAcquire(int permits, Deadline deadline) throws InterruptedException, ShutdownException {
    if (!firstGuard.tryAcquire(permits, deadline)) return false;
    boolean acquired = false;
    try {
      return acquired = secondGuard.tryAcquire(permits, deadline);
    } finally {
      if (!acquired) firstGuard.release(permits);
    }
  }

  @Override
  public void acquire(int permits) throws InterruptedException, ShutdownException {
    firstGuard.acquire(permits);
    try {
      secondGuard.acquire(permits);
    } catch (Exception e) {
      firstGuard.release(permits);
      throw e;
    }
  }

  @Override
  public void release(int permits) {
    secondGuard.release(permits);
    firstGuard.release(permits);
  }

  @Override
  protected Iterable<? extends ComposableService> getComponentServices() {
    return Arrays.asList(firstGuard, secondGuard);
  }
}
