package upstart.example;


import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import upstart.services.ServiceDependencyChecker;
import upstart.test.AfterInjection;
import upstart.test.AfterServiceStarted;
import upstart.test.BeforeServiceStopped;
import upstart.test.UpstartServiceTest;
import org.junit.jupiter.api.Test;
import upstart.test.UpstartTestBuilder;

import javax.inject.Inject;

import java.time.Duration;

import static com.google.common.truth.Truth.assertThat;
import static org.awaitility.Awaitility.await;

@UpstartServiceTest(UpstartExampleApp.class)
class UpstartExampleAppTest {
  @Inject
  ServiceDependencyChecker dependencyChecker;
  @Inject
  CountReportingService reportingService;

  int expectedLifecycleAssertions = 3;

  @BeforeEach
  void adjustConfigs(UpstartTestBuilder testBuilder) {
    testBuilder.subConfig("example-app", c -> c
            .overrideConfig("counting.updatePeriod", Duration.ofMillis(200))
            .overrideConfig("reporting.reportPeriod", Duration.ofMillis(100))
    );
  }

  @Test
  void testStuff() {
    await().atMost(Duration.ofSeconds(2))
            .untilAsserted(() -> assertThat(reportingService.latestCount()).isAtLeast(3));
  }

  @AfterInjection
  void doStuffBeforeServicesStart() {
    // double-check assumptions about startup/shutdown order with ServiceDependencyChecker
    dependencyChecker.assertThat(CountReportingService.class).dependsUpon(CountingService.class);
    expectedLifecycleAssertions--;
  }

  @AfterServiceStarted
  void beforeEachAfterServicesStarted() {
    assertThat(reportingService.isRunning()).isTrue();
    expectedLifecycleAssertions--;
  }

  @BeforeServiceStopped
  void afterEachBeforeServicesStopped() {
    assertThat(reportingService.isRunning()).isTrue();
    expectedLifecycleAssertions--;
  }

  @AfterEach
  void checkLifecycleCallbacksAreInvoked() {
    assertThat(expectedLifecycleAssertions).isEqualTo(0);
  }
}