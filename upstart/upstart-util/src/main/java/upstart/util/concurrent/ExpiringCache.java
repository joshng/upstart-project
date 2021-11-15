package upstart.util.concurrent;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.function.Supplier;

import static com.google.common.base.Preconditions.checkArgument;

public abstract class ExpiringCache<T> implements Supplier<T> {
  private final Clock clock;
  private final Duration timeToLive;
  private volatile T value;
  private volatile Instant expiry = Instant.MIN;

  public static <T> ExpiringCache<T> from(Clock clock, Duration timeToLive, Supplier<T> source) {
    return new ExpiringCache<T>(clock, timeToLive) {
      @Override
      protected T refresh() {
        return source.get();
      }
    };
  }

  public ExpiringCache(Clock clock, Duration timeToLive) {
    checkArgument(timeToLive.toNanos() > 0, "timeToLive must be > 0", timeToLive);
    this.clock = clock;
    this.timeToLive = timeToLive;
  }

  public T get() {
    Instant now = clock.instant();
    Instant deadline = expiry;
    if (deadline.isBefore(now)) {
      synchronized (this) {
        if (deadline == expiry) {
          T newValue = refresh();
          value = newValue;
          expiry = now.plus(timeToLive);
          return newValue;
        }
      }
    }
    return value;
  }

  protected abstract T refresh();
}
