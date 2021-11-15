package upstart.test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static com.google.common.base.Preconditions.checkArgument;

public class FakeClock extends Clock {
  private volatile Instant now;
  private final ZoneId zoneId;


  public FakeClock(Instant initialTime) {
    this(initialTime, ZoneOffset.UTC);
  }

  public synchronized Instant advance(Duration duration) {
    if (!duration.isZero()) {
      return advance(now.plus(duration));
    } else {
      return now;
    }
  }

  public synchronized Instant advance(Instant newTime) {
    checkArgument(!newTime.isBefore(now), "FakeClock should not move backwards; was %s, requested %s", now, newTime);
    now = newTime;
    return newTime;
  }

  public FakeClock(Instant now, ZoneId zoneId) {
    this.now = now;
    this.zoneId = zoneId;
  }

  @Override
  public ZoneId getZone() {
    return zoneId;
  }

  @Override
  public Clock withZone(ZoneId zone) {
    return new Clock() {
      @Override
      public ZoneId getZone() {
        return zone;
      }

      @Override
      public Clock withZone(ZoneId zone) {
        return FakeClock.this.withZone(zone);
      }

      @Override
      public Instant instant() {
        return now;
      }
    };
  }

  @Override
  public Instant instant() {
    return now;
  }
}
