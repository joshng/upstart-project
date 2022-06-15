package upstart.util.concurrent.services;

import com.google.common.util.concurrent.Service;
import com.google.common.util.concurrent.ServiceManager;
import org.junit.jupiter.api.Test;
import upstart.util.concurrent.services.test.ChoreographedLifecycleService;
import upstart.test.StacklessTestException;
import upstart.util.concurrent.Deadline;
import upstart.util.concurrent.Threads;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.google.common.truth.Truth.assertThat;
import static upstart.test.truth.CompletableFutureSubject.assertThat;

public class AggregateServiceTest {
  static {
    Logger.getLogger(ServiceManager.class.getName()).setLevel(Level.OFF);

    // TODO figure out why log4j#setLevel below causes the logs to REAPPEAR after the j.u.l.Logger#setLevel above has hidden them!?
//    LogManager.getLogger(ServiceManager.class).setLevel(Level.OFF);
  }

  @Test
  void testFailureHandling() throws InterruptedException {
    for (int i = 0; i < 1; i++) {
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

      assertThat(normalService.state()).isEqualTo(Service.State.STOPPING);
      assertThat(normalService.getStoppedFuture()).isNotDone();

      Threads.sleep(Duration.ofMillis(250));

      assertThat(aggregateService.getStoppedFuture()).isNotDone();
      assertThat(aggregateService.getTerminationFuture()).isNotDone();

      normalService.shutdownGate.open();

      assertThat(started).doneWithin(deadline).failedWith(StacklessTestException.class);

      try {
        assertThat(aggregateService.getTerminationFuture()).doneWithin(deadline).havingResultThat().isEqualTo(
                Service.State.FAILED);
      } catch (Throwable e) {
        System.out.println(aggregateService);
        throw e;
      }
      assertThat(aggregateService.state()).isEqualTo(Service.State.FAILED);
    }
  }
}
