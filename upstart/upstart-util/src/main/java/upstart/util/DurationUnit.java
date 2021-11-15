package upstart.util;

import java.time.Duration;
import java.time.temporal.Temporal;
import java.time.temporal.TemporalUnit;

// thank you stackoverflow
public final class DurationUnit implements TemporalUnit {

  private static final int SECONDS_PER_DAY = 86400;
  private static final long NANOS_PER_SECOND =  1000_000_000L;
  private static final long NANOS_PER_DAY = NANOS_PER_SECOND * SECONDS_PER_DAY;

  private final Duration duration;

  public static DurationUnit of(Duration duration)   { return new DurationUnit(duration); }
  public static DurationUnit ofDays(long days)       { return new DurationUnit(Duration.ofDays(days)); }
  public static DurationUnit ofHours(long hours)     { return new DurationUnit(Duration.ofHours(hours)); }
  public static DurationUnit ofMinutes(long minutes) { return new DurationUnit(Duration.ofMinutes(minutes)); }
  public static DurationUnit ofSeconds(long seconds) { return new DurationUnit(Duration.ofSeconds(seconds)); }
  public static DurationUnit ofMillis(long millis)   { return new DurationUnit(Duration.ofMillis(millis)); }
  public static DurationUnit ofNanos(long nanos)     { return new DurationUnit(Duration.ofNanos(nanos)); }

  private DurationUnit(Duration duration) {
    if (duration.isZero() || duration.isNegative())
      throw new IllegalArgumentException("Duration may not be zero or negative");
    this.duration = duration;
  }

  @Override
  public Duration getDuration() {
    return duration;
  }

  @Override
  public boolean isDurationEstimated() {
    return duration.getSeconds() >= SECONDS_PER_DAY;
  }

  @Override
  public boolean isDateBased() {
    return duration.getNano() == 0 && this.duration.getSeconds() % SECONDS_PER_DAY == 0;
  }

  @Override
  public boolean isTimeBased() {
    return duration.getSeconds() < SECONDS_PER_DAY && NANOS_PER_DAY % duration.toNanos() == 0;
  }

  @Override
  @SuppressWarnings("unchecked")
  public <R extends Temporal> R addTo(R temporal, long amount) {
    return (R) duration.multipliedBy(amount).addTo(temporal);
  }

  @Override
  public long between(Temporal temporal1Inclusive, Temporal temporal2Exclusive) {
    return Duration.between(temporal1Inclusive, temporal2Exclusive).dividedBy(this.duration);
  }

  @Override
  public String toString() {
    return duration.toString();
  }
}