package upstart.util.concurrent;

import com.google.common.util.concurrent.RateLimiter;
import upstart.util.exceptions.UncheckedInterruptedException;
import upstart.util.functions.MoreFunctions;

import java.time.Duration;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;

public class AsyncFlowController extends ClosableSemaphore {
  public static final Duration DEFAULT_SHUTDOWN_POLL_PERIOD = Duration.ofMillis(500);
  private final RateLimiter rateLimiter;
  private final long shutdownPollPeriod;

  public AsyncFlowController(int maxInflightJobs, RateLimiter rateLimiter) {
    this(maxInflightJobs, rateLimiter, DEFAULT_SHUTDOWN_POLL_PERIOD);
  }

  public AsyncFlowController(int maxInflightJobs, RateLimiter rateLimiter, Duration shutdownPollPeriod) {
    super(maxInflightJobs);
    this.rateLimiter = rateLimiter;
    this.shutdownPollPeriod = shutdownPollPeriod.toNanos();
  }

  public <I, O> Function<I, Promise<O>> flowControlledFunction(
          Function<? super I, ? extends CompletionStage<O>> job
  ) {
    return input -> getWithFlowControl(MoreFunctions.bind(input, job));
  }

  public <O> Supplier<Promise<O>> flowControlledSupplier(Supplier<? extends CompletionStage<O>> job) {
    return () -> getWithFlowControl(job);
  }

  public <O> Promise<O> getWithFlowControl(Supplier<? extends CompletionStage<O>> job) {
    UncheckedInterruptedException.propagate(this::acquire);
    return Promise.of(job.get()).uponCompletion(this::release);
  }

  @Override
  public void acquire(int permits) throws ShutdownException, InterruptedException {
    super.acquire(permits);
    do {
      checkClosed();
    } while (!rateLimiter.tryAcquire(shutdownPollPeriod, TimeUnit.NANOSECONDS));
  }
}
