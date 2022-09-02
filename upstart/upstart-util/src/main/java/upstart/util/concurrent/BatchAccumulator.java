package upstart.util.concurrent;

import com.google.common.collect.Comparators;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class BatchAccumulator<B> {
  private final Supplier<B> newBatchSupplier;
  private final Consumer<B> completedBatchConsumer;
  private final Duration idleTimeout;
  private final Duration maxBufferLatency;
  private final Scheduler scheduler;
  private final Deadline.Clock deadlineClock;

  private BatchTimeout currentTimeout = null;

  /**
   * This constructor is protected to prevent its use except by subclasses; use {@link Factory#create} instead to
   * support interception in tests.
   */
  protected BatchAccumulator(
          Supplier<B> newBatchSupplier,
          Consumer<B> completedBatchConsumer,
          Duration idleTimeout,
          Duration maxBufferLatency,
          Scheduler scheduler
  ) {
    this.newBatchSupplier = newBatchSupplier;
    this.completedBatchConsumer = completedBatchConsumer;
    this.idleTimeout = idleTimeout;
    this.maxBufferLatency = maxBufferLatency;
    this.scheduler = scheduler;
    deadlineClock = Deadline.clock(scheduler.clock());
  }

  public synchronized <I> Deadline accumulate(I input, BatchBuilder<I, B> action) {
    Instant now = scheduler.now();

    BatchTimeout timeout;
    I remainingInput = input;
    do {
      timeout = currentTimeout;
      if (timeout == null) currentTimeout = timeout = new BatchTimeout(now);

      AccumulationResult<I> result = action.addToBatch(remainingInput, timeout.batch);

      if (result.closeBatch()) timeout.close();

      remainingInput = result instanceof BatchAccumulator.RejectedResult<I> rejected
              ? rejected.rejectedInput()
              : null;
    } while (remainingInput != null);

    return timeout.touchDeadline(now);
  }

  public synchronized void flush() {
    Optional.ofNullable(currentTimeout).ifPresent(BatchTimeout::close);
  }

  public static <I> AccumulationResult<I> accepted(boolean stillOpen) {
    return stillOpen ? accepted() : acceptedAndClosed();
  }

  @SuppressWarnings("unchecked")
  public static <I> AccumulationResult<I> accepted() {
    return (AccumulationResult<I>) AcceptedResult.INSTANCE;
  }

  @SuppressWarnings("unchecked")
  public static <I> AccumulationResult<I> acceptedAndClosed() {
    return (AccumulationResult<I>) AcceptedAndClosedResult.INSTANCE;
  }

  public static <I> AccumulationResult<I> rejected(I rejectedInput) {
    return new RejectedResult<>(rejectedInput);
  }

  private class BatchTimeout {
    private final B batch = newBatchSupplier.get();
    private final Instant accumulationDeadline;
    private Deadline idleDeadline;

    BatchTimeout(Instant now) {
      accumulationDeadline = now.plus(maxBufferLatency);
      scheduleTimeout(idleTimeout);
      touchDeadline(now);
    }

    Deadline touchDeadline(Instant now) {
      return idleDeadline = deadlineClock.deadlineAt(Comparators.min(now.plus(idleTimeout), accumulationDeadline));
    }

    void scheduleTimeout(Duration delay) {
      scheduler.schedule(delay, this::onTimeout);
    }

    private void onTimeout() {
      synchronized (BatchAccumulator.this) {
        Deadline deadline = idleDeadline;
        if (deadline != null) {
          Duration remaining = deadline.remaining();
          boolean isExpired = remaining.isNegative() || remaining.isZero(); // todo: change to isPositive() with java 18
          if (!isExpired) {
            scheduleTimeout(remaining);
          } else {
            close();
          }
        }
      }
    }

    private void close() {
      currentTimeout = null;
      idleDeadline = null;
      completedBatchConsumer.accept(batch);
    }
  }

  @FunctionalInterface
  public interface BatchBuilder<I, B> {
    BatchAccumulator.AccumulationResult<I> addToBatch(I input, B batch);

  }


  public sealed interface AccumulationResult<I> {
    boolean closeBatch();

    static <I> AccumulationResult<I> accepted(boolean stillOpen) {
      return stillOpen ? AccumulationResult.accepted() : AccumulationResult.acceptedAndClosed();
    }

    @SuppressWarnings("unchecked")
    static <I> AccumulationResult<I> accepted() {
      return (AccumulationResult<I>) AcceptedResult.INSTANCE;
    }

    @SuppressWarnings("unchecked")
    static <I> AccumulationResult<I> acceptedAndClosed() {
      return (AccumulationResult<I>) AcceptedAndClosedResult.INSTANCE;
    }

    static <I> AccumulationResult<I> rejected(I rejectedInput) {
      return new RejectedResult<>(rejectedInput);
    }
  }

  private static final class AcceptedResult<I> implements AccumulationResult<I> {
    private static final AcceptedResult<Object> INSTANCE = new AcceptedResult<>();
    @Override
    public boolean closeBatch() {
      return false;
    }
  }

  private static final class AcceptedAndClosedResult<I> implements AccumulationResult<I> {
    private static final AcceptedResult<Object> INSTANCE = new AcceptedResult<>();
    @Override
    public boolean closeBatch() {
      return true;
    }
  }

  private record RejectedResult<I>(I rejectedInput) implements AccumulationResult<I> {
    @Override
    public boolean closeBatch() {
      return true;
    }
  }

  public static class Factory {
    public <B> BatchAccumulator<B> create(
            Supplier<B> newBatchSupplier,
            Consumer<B> completedBatchConsumer,
            Duration idleTimeout,
            Duration maxBufferLatency,
            Scheduler scheduler
    ) {
      return createStandard(newBatchSupplier, completedBatchConsumer, idleTimeout, maxBufferLatency, scheduler);
    }

    protected <B> BatchAccumulator<B> createStandard(
            Supplier<B> newBatchSupplier,
            Consumer<B> completedBatchConsumer,
            Duration idleTimeout,
            Duration maxBufferLatency,
            Scheduler scheduler
    ) {
      return new BatchAccumulator<>(newBatchSupplier, completedBatchConsumer, idleTimeout, maxBufferLatency, scheduler);
    }
  }
}
