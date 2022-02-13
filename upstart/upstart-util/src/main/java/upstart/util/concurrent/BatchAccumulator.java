package upstart.util.concurrent;

import com.google.common.collect.Ordering;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class BatchAccumulator<B> {
  private final Supplier<B> newBatchSupplier;
  private final Consumer<B> completedBatchConsumer;
  private final Duration idleTimeout;
  private final Duration maxBufferLatency;
  private final Scheduler scheduler;

  private BatchTimeout currentTimeout = null;

  public BatchAccumulator(
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
  }

  public synchronized <I> Duration accumulate(I input, BiFunction<B, I, Optional<I>> action) {
    Instant now = scheduler.now();

    BatchTimeout timeout;
    Optional<I> remainingInput = Optional.of(input);
    do {
      timeout = currentTimeout;
      if (timeout == null) {
        currentTimeout = timeout = new BatchTimeout(now);
      }
      remainingInput = action.apply(timeout.batch, remainingInput.get());
      if (remainingInput.isPresent()) {
        timeout.close();
      }
    } while (remainingInput.isPresent());

    return Duration.between(now, timeout.touchDeadline(now));
  }

  public synchronized void flush() {
    Optional.ofNullable(currentTimeout).ifPresent(BatchTimeout::close);
  }

  private class BatchTimeout {
    private final B batch = newBatchSupplier.get();
    private final Instant accumulationDeadline;
    private Instant idleDeadline;

    BatchTimeout(Instant now) {
      accumulationDeadline = now.plus(maxBufferLatency);
      scheduleTimeout(idleTimeout);
      touchDeadline(now);
    }

    Instant touchDeadline(Instant now) {
      return idleDeadline = Ordering.natural().min(now.plus(idleTimeout), accumulationDeadline);
    }

    void scheduleTimeout(Duration delay) {
      scheduler.schedule(delay, this::onTimeout);
    }

    private void onTimeout() {
      synchronized (BatchAccumulator.this) {
        Instant deadline = idleDeadline;
        if (deadline != null) {
          Instant now = scheduler.now();
          if (now.isBefore(deadline)) {
            scheduleTimeout(Duration.between(now, deadline));
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
}
