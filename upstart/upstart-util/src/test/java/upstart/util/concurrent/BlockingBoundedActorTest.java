package upstart.util.concurrent;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.google.common.truth.Truth.assertThat;

class BlockingBoundedActorTest {
  @Test
  void failureAbortsQueuedRequests() throws InterruptedException {
    var a = new BlockingBoundedActor(5);

    CountDownLatch latch =new CountDownLatch(1);
    Deadline deadline = Deadline.withinSeconds(5);
    ExecutorService executor = Executors.newCachedThreadPool();
    Promise<Object> firstFailure = a.request(() -> {
      deadline.await(latch);
      throw new RuntimeException("fake exception for test");
    }, executor);

    Promise<Integer> doomedRequest = a.request(() -> 5, executor);
    latch.countDown();
    assertCompletedExceptionally(firstFailure, deadline);
    assertCompletedExceptionally(doomedRequest, deadline);


  }

  private void assertCompletedExceptionally(
          Promise<?> doomedRequest, Deadline deadline
  ) throws InterruptedException {
    assertThat(deadline.awaitDone(doomedRequest)).isTrue();
    assertThat(doomedRequest.isCompletedExceptionally()).isTrue();
  }

}