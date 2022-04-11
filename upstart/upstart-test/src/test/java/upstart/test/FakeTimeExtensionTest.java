package upstart.test;

import com.google.common.truth.Truth;
import org.junit.jupiter.api.Test;
import upstart.config.UpstartModule;
import upstart.util.concurrent.Promise;
import upstart.util.concurrent.services.ScheduledService;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

import static com.google.common.truth.Truth.assertThat;
import static upstart.test.CompletableFutureSubject.assertThat;

@UpstartServiceTest
@FakeTimeTest(interceptSchedules = FakeTimeExtensionTest.FakeScheduledService.class)
class FakeTimeExtensionTest extends UpstartModule {
  @Inject FakeScheduledService service;
  @Override
  protected void configure() {
    serviceManager().manage(FakeScheduledService.class);
  }

  @Test
  void scheduleIsSynthesized(FakeTime fakeTime) {
    assertThat(service.counter.get()).isEqualTo(0);
    assertThat(service.timestamp).isNotDone();

    fakeTime.advance(Duration.ofDays(2));
    assertThat(service.timestamp).completedWithResultThat().isEqualTo(Instant.EPOCH);
    assertThat(service.counter.get()).isEqualTo(3);

  }

  @Singleton
  static class FakeScheduledService extends ScheduledService {
    final AtomicInteger counter = new AtomicInteger();
    final Promise<Instant> timestamp = new Promise<>();
    final Clock clock;

    @Inject
    FakeScheduledService(Clock clock) {
      this.clock = clock;
    }

    @Override
    protected void runOneIteration() throws Exception {
      counter.incrementAndGet();
      timestamp.complete(clock.instant());
    }

    @Override
    protected Schedule schedule() {
      return fixedRateSchedule(Duration.ZERO, Duration.ofDays(1));
    }
  }

}