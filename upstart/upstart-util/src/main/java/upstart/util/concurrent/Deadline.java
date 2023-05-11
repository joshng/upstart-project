package upstart.util.concurrent;

import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;

public sealed abstract class Deadline {
  public static final Clock SYSTEM_CLOCK = new Clock(InstantSource.system());

  public static final Deadline NONE = Deadline.unbounded(Instant.EPOCH);
  private final Instant startInstant;
  protected final InstantSource clock;

  public static Clock clock(InstantSource clock) {
    return new Clock(clock);
  }

  public static Clock systemClock() {
    return SYSTEM_CLOCK;
  }

  public static Deadline withinSeconds(int seconds) {
    return within(Duration.ofSeconds(seconds));
  }

  public static Deadline withinMinutes(int minutes) {
    return within(Duration.ofMinutes(minutes));
  }

  public static Deadline withinHours(int hours) {
    return within(Duration.ofHours(hours));
  }

  public static Deadline within(Duration duration) {
    return within(duration, InstantSource.system());
  }

  public static Deadline within(long duration, TimeUnit unit) {
    return within(Duration.ofNanos(unit.toNanos(duration)));
  }

  public static Deadline at(Instant deadline) {
    return at(deadline, InstantSource.system());
  }

  public static Deadline at(Instant deadline, InstantSource clock) {
    Instant now = clock.instant();
    return deadline.isBefore(Instant.MAX)
            ? new Bounded(now, Duration.between(now, deadline), deadline, clock)
            : unbounded(now, clock);
  }

  public static Deadline within(Duration duration, InstantSource clock) {
    return from(clock.instant(), duration, clock);
  }

  public static Deadline unbounded(InstantSource clock) {
    return unbounded(clock.instant(), clock);
  }

  public static Deadline unbounded(Instant startInstant) {
    return unbounded(startInstant, InstantSource.system());
  }

  public static Deadline unbounded(Instant startInstant, InstantSource clock) {
    return new Unbounded(startInstant, clock);
  }

  public static Deadline from(Instant startInstant, Duration duration, InstantSource clock) {
    return duration.toNanos() == Long.MAX_VALUE
            ? unbounded(startInstant, clock)
            : new Bounded(startInstant, duration, startInstant.plus(duration), clock);
  }

  private Deadline(Instant startInstant, InstantSource clock) {
    this.startInstant = startInstant;
    this.clock = clock;
  }

  public Instant startInstant() {
    return startInstant;
  }

  public abstract Duration initialDuration();

  public abstract Instant deadline();

  public Duration expiredSinceStart() {
    return Duration.between(startInstant, clock.instant());
  }

  public abstract Duration remaining();

  public abstract Duration remaining(Instant instant);

  public boolean isExpired() {
    return isExpired(clock.instant());
  }

  public abstract boolean isExpired(Instant instant);

  public abstract void wait(Object syncRoot) throws InterruptedException;

  public abstract boolean awaitDone(Future<?> future) throws InterruptedException;

  public abstract boolean tryLock(Lock lock) throws InterruptedException;

  public abstract boolean await(Condition condition) throws InterruptedException;

  public abstract boolean await(CountDownLatch latch) throws InterruptedException;

  private static final class Bounded extends Deadline {
    private final Duration initialDuration;
    private final Instant deadline;

    public Bounded(
            Instant startInstant,
            Duration duration,
            Instant deadline,
            InstantSource clock
    ) {
      super(startInstant, clock);
      this.initialDuration = duration;
      this.deadline = deadline;
    }

    @Override
    public Duration initialDuration() {
      return initialDuration;
    }

    @Override
    public Instant deadline() {
      return deadline;
    }

    @Override
    public Duration remaining() {
      return remaining(clock.instant());
    }

    @Override
    public Duration remaining(Instant instant) {
      return Duration.between(instant, deadline);
    }

    @Override
    public boolean isExpired(Instant instant) {
      return remaining(instant).compareTo(Duration.ZERO) <= 0;
    }

    @Override
    public void wait(Object syncRoot) throws InterruptedException {
      syncRoot.wait(remaining().toMillis());
    }

    @Override
    public boolean awaitDone(Future<?> future) throws InterruptedException {
      if (future.isDone()) return true;
      try {
        future.get(remainingNanos(), TimeUnit.NANOSECONDS);
      } catch (ExecutionException ignored) {
      } catch (TimeoutException e) {
        return false;
      }
      return true;
    }

    @Override
    public boolean tryLock(Lock lock) throws InterruptedException {
      return lock.tryLock(remainingNanos(), TimeUnit.NANOSECONDS);
    }

    @Override
    public boolean await(Condition condition) throws InterruptedException {
      return condition.await(remainingNanos(), TimeUnit.NANOSECONDS);
    }

    @Override
    public boolean await(CountDownLatch latch) throws InterruptedException {
      return latch.await(remainingNanos(), TimeUnit.NANOSECONDS);
    }

    private long remainingNanos() {
      return remaining().toNanos();
    }

    @Override
    public String toString() {
      return "Deadline{" +
              "startInstant=" + startInstant() +
              ", initialDuration=" + initialDuration +
              ", deadline=" + deadline +
              '}';
    }
  }

  private static final class Unbounded extends Deadline {
    private static final Duration MAX_DURATION = Duration.ofNanos(Long.MAX_VALUE);

    private Unbounded(Instant startInstant, InstantSource clock) {
      super(startInstant, clock);
    }

    @Override
    public Duration initialDuration() {
      return MAX_DURATION;
    }

    @Override
    public Instant deadline() {
      return Instant.MAX;
    }

    @Override
    public Duration remaining() {
      return MAX_DURATION;
    }

    @Override
    public Duration remaining(Instant instant) {
      return MAX_DURATION;
    }

    @Override
    public boolean isExpired(Instant instant) {
      return false;
    }

    @Override
    public void wait(Object syncRoot) throws InterruptedException {
      syncRoot.wait();
    }

    @Override
    public boolean awaitDone(Future<?> future) throws InterruptedException {
      try {
        future.get();
      } catch (ExecutionException e) {
        // ignored
      }
      return true;
    }

    @Override
    public boolean await(Condition condition) throws InterruptedException {
      condition.await();
      return true;
    }

    @Override
    public boolean await(CountDownLatch latch) throws InterruptedException {
      latch.await();
      return true;
    }

    @Override
    public boolean tryLock(Lock lock) throws InterruptedException {
      lock.lockInterruptibly();
      return true;
    }
  }

  public static class Clock implements InstantSource {
    private final InstantSource clock;

    public Clock(InstantSource clock) {
      this.clock = clock;
    }

    @Override
    public Instant instant() {
      return clock.instant();
    }

    public Deadline deadlineWithinMinutes(long minutes) {
      return deadlineWithin(Duration.ofMinutes(minutes));
    }

    public Deadline deadlineWithinSeconds(long seconds) {
      return deadlineWithin(Duration.ofSeconds(seconds));
    }

    public Deadline WithinMillis(long millis) {
      return deadlineWithin(Duration.ofMillis(millis));
    }

    public Deadline deadlineWithinNanos(long nanos) {
      return deadlineWithin(Duration.ofNanos(nanos));
    }

    public Deadline deadlineWithin(Duration duration) {
      return Deadline.within(duration, clock);
    }

    public Deadline deadlineWithin(long duration, TimeUnit unit) {
      return deadlineWithinNanos(unit.toNanos(duration));
    }

    public Deadline deadlineAt(Instant deadline) {
      return Deadline.at(deadline, clock);
    }

    public Deadline deadlineFrom(Instant startInstant, Duration duration) {
      return Deadline.from(startInstant, duration, clock);
    }


    public Deadline unboundedDeadline() {
      return Deadline.unbounded(clock);
    }

    public Deadline unboundedDeadlineFrom(Instant startInstant) {
      return Deadline.unbounded(startInstant, clock);
    }
  }
}
