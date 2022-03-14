package upstart.services;

import com.google.common.util.concurrent.Service;
import org.junit.jupiter.api.Test;
import upstart.services.test.ChoreographedLifecycleService;
import upstart.test.StacklessTestException;
import upstart.util.concurrent.Deadline;
import upstart.util.concurrent.Threads;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static com.google.common.truth.Truth.assertThat;
import static upstart.test.CompletableFutureSubject.assertThat;

public class AggregateServiceTest {
  @Test
  void testFailureHandling() throws InterruptedException {
    for (int i = 0; i < 100; i++) {
      Deadline deadline = Deadline.withinSeconds(4);
    var normalService = new ChoreographedLifecycleService();
    var failingService = new ChoreographedLifecycleService();
    AggregateService aggregateService = new AggregateService() {
      @Override
      protected Iterable<? extends ComposableService> getComponentServices() {
        return List.of(normalService, failingService);
      }
    };

    CompletableFuture<Service.State> started = aggregateService.start();

    assertThat(normalService.startupGate.requested).doneWithin(deadline).completedNormally();

    normalService.startupGate.open();
    assertThat(aggregateService.state()).isEqualTo(Service.State.STARTING);

      failingService.shutdownGate.open();
    failingService.startupGate.prepareFailure(new StacklessTestException());
    assertThat(aggregateService.getTerminationFuture()).isNotDone();

    assertThat(normalService.shutdownGate.requested).doneWithin(deadline)
            .completedNormally();
    assertThat(normalService.shutdownGate.ready).isNotDone();

    // TODO delete me
    System.out.println("... " + deadline.expired() + "\n" + aggregateService);

//    await().atMost(Duration.ofSeconds(1)).untilAsserted(() -> assertThat(aggregateService.state()).isEqualTo(Service.State.STOPPING));

    assertThat(normalService.state()).isEqualTo(Service.State.STOPPING);
    assertThat(normalService.getStoppedFuture()).isNotDone();

    Threads.sleep(Duration.ofMillis(250));

      assertThat(aggregateService.getStoppedFuture()).isNotDone();
      assertThat(aggregateService.getTerminationFuture()).isNotDone();

    normalService.shutdownGate.open();

    assertThat(started).doneWithin(deadline).failedWith(StacklessTestException.class);

    try {
      assertThat(aggregateService.getTerminationFuture()).doneWithin(deadline).completedWithResultThat().isEqualTo(Service.State.FAILED);
    } catch (Throwable e) {
      System.out.println(aggregateService);
      throw e;
    }
    assertThat(aggregateService.state()).isEqualTo(Service.State.FAILED);
    }
  }
}
