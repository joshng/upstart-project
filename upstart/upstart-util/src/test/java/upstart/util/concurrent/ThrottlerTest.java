package upstart.util.concurrent;

import com.google.common.base.Ticker;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

class ThrottlerTest {
  private final FakeTicker ticker = new FakeTicker();
  private final Throttler throttler = new Throttler(1, TimeUnit.SECONDS, ticker);

  @Test
  void tryAcquireThrottles() {
    assertAvailable();
    assertThrottled();
    ticker.advance(1, TimeUnit.SECONDS);
    assertAvailable();
    assertThrottled();
    ticker.advance(500, TimeUnit.MILLISECONDS);
    assertThrottled();
  }

  @DisabledOnOs(value = {OS.OTHER, OS.LINUX}, disabledReason = "this test can fail in docker; Thread.sleep returns too early!")
  @Test
  void acquireBlocks() throws InterruptedException {
    Instant lastAcquired = Instant.now();
    assertAvailable();
    throttler.acquire();
    Instant nextAcquired = Instant.now();
    assertThat(Duration.between(lastAcquired, nextAcquired)).isAtLeast(Duration.ofSeconds(1));
  }

  private void assertThrottled() {
    assertAvailable(false);
  }

  private void assertAvailable(boolean available) {
    assertWithMessage("shouldBeAvailable").that(throttler.mayBeAvailable()).isEqualTo(available);
    assertWithMessage("tryAcquire").that(throttler.tryAcquire()).isEqualTo(available);
  }

  private void assertAvailable() {
    assertAvailable(true);
  }

  private static class FakeTicker extends Ticker {
    long now = 0;

    @Override
    public long read() {
      return now;
    }

    void advance(long quantity, TimeUnit unit) {
      now += unit.toNanos(quantity);
    }
  }
}