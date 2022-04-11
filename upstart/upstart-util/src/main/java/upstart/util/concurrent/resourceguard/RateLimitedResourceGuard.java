package upstart.util.concurrent.resourceguard;

import com.google.common.collect.Comparators;
import com.google.common.util.concurrent.RateLimiter;
import upstart.util.concurrent.Deadline;
import upstart.util.concurrent.ShutdownException;
import upstart.util.concurrent.services.LightweightService;

import java.time.Duration;

public class RateLimitedResourceGuard extends LightweightService implements BoundedResourceGuard {
  public static final Duration DEFAULT_SHUTDOWN_POLL_PERIOD = Duration.ofMillis(300);

  private final Duration shutdownPollPeriod;
  private final RateLimiter rateLimiter;

  public RateLimitedResourceGuard(double requestsPerSec) {
    this(requestsPerSec, DEFAULT_SHUTDOWN_POLL_PERIOD);
  }

  public RateLimitedResourceGuard(double requestsPerSec, Duration shutdownPollPeriod) {
    this(RateLimiter.create(requestsPerSec), shutdownPollPeriod);
  }

  public RateLimitedResourceGuard(RateLimiter rateLimiter, Duration shutdownPollPeriod) {
    this.shutdownPollPeriod = shutdownPollPeriod;
    this.rateLimiter = rateLimiter;
  }

  @Override
  protected void startUp() throws Exception {
  }

  @Override
  protected void shutDown() throws Exception {
  }

  @Override
  public boolean tryAcquire(int permits) {
    throwIfShutDown();
    return rateLimiter.tryAcquire(permits);
  }

  @Override
  public boolean tryAcquire(int permits, Deadline deadline) throws ShutdownException {
    Duration remaining;
    do {
      throwIfShutDown();
      remaining = deadline.remaining();
      if (remaining.compareTo(Duration.ZERO) <= 0) {
        return false;
      }
    } while (!rateLimiter.tryAcquire(Comparators.min(shutdownPollPeriod, remaining)));

    return true;
  }

  @Override
  public void acquire(int permits) throws ShutdownException {
    tryAcquire(permits, Deadline.NONE);
  }

  @Override
  public void release(int permits) {
  }
}
