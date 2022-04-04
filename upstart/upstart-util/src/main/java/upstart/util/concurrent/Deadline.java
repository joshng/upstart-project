package upstart.util.concurrent;

import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class Deadline {
  private final Instant startInstant;
  private final Duration initialDuration;
  private final Instant deadline;
  private final InstantSource clock;

  public static Deadline withinSeconds(int seconds) {
    return within(Duration.ofSeconds(seconds));
  }

  public static Deadline within(Duration duration) {
    return within(duration, InstantSource.system());
  }

  public static Deadline within(long duration, TimeUnit unit) {
    return within(Duration.ofNanos(unit.toNanos(duration)));
  }

  public static Deadline within(Duration duration, InstantSource clock) {
    return from(clock.instant(), duration, clock);
  }

  public static Deadline from(Instant startInstant, Duration duration, InstantSource clock) {
    return new Deadline(startInstant, duration, clock);
  }

  private Deadline(Instant startInstant, Duration duration, InstantSource clock) {
    this.startInstant = startInstant;
    initialDuration = duration;
    this.deadline = startInstant.plus(duration);
    this.clock = clock;
  }

  public Instant startInstant() {
    return startInstant;
  }

  public Duration initialDuration() {
    return initialDuration;
  }

  public Instant deadline() {
    return deadline;
  }

  public Duration expired() {
    return Duration.between(startInstant, clock.instant());
  }

  public Duration remaining() {
    return Duration.between(clock.instant(), deadline);
  }

  public boolean isExpired() {
    return remaining().isNegative();
  }

  public void wait(Object syncRoot) throws InterruptedException {
    syncRoot.wait(remaining().toMillis());
  }

  public boolean awaitDone(Future<?> future) throws InterruptedException {
    if (future.isDone()) return true;
    try {
      future.get(remaining().toNanos(), TimeUnit.NANOSECONDS);
    } catch (ExecutionException ignored) {
    } catch (TimeoutException e) {
      return false;
    }
    return true;
  }
}
