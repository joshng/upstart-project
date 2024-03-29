package upstart.util.concurrent;

import org.junit.jupiter.api.Test;
import upstart.ExecutorServiceScheduler;
import upstart.test.FakeTime;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

import static com.google.common.truth.Truth.assertThat;

public class BatchAccumulatorTest {
  private static final Duration IDLE_TIMEOUT = Duration.ofMillis(100);
  private static final Duration MAX_BUFFER_LATENCY = Duration.ofMillis(500);
  private final List<List<String>> batches = new ArrayList<>(10);
  private final FakeTime time = new FakeTime(Instant.EPOCH, ZoneOffset.UTC);

  @Test
  void testABatch() {

    BatchAccumulator<List<String>> accumulator = new BatchAccumulator.Factory().create(
            () -> new ArrayList<>(4),
            batches::add,
            IDLE_TIMEOUT,
            MAX_BUFFER_LATENCY,
            new ExecutorServiceScheduler(
                    () -> Duration.ZERO,
                    time.scheduledExecutor(Executors.newCachedThreadPool()),
                    time.clock()
            )
    );

    BatchAccumulator.BatchBuilder<String, List<String>> batchBuilder = (input, batch) -> {
      if (batch.size() < 4) {
        batch.add(input);
        return BatchAccumulator.accepted();
      } else {
        return BatchAccumulator.rejected(input);
      }
    };
    accumulator.accumulate("a", batchBuilder);
    accumulator.accumulate("b", batchBuilder);

    assertThat(batches).isEmpty();

    time.advance(IDLE_TIMEOUT);

    assertThat(batches).contains(List.of("a", "b"));
  }

  @Test
  void testManyConcurrentInsertions() {
    ConcurrentLinkedQueue<List<String>> batches = new ConcurrentLinkedQueue<>();
    BatchAccumulator<List<String>> accumulator = new BatchAccumulator.Factory().create(
            () -> new ArrayList<>(4),
            batches::offer,
            IDLE_TIMEOUT,
            MAX_BUFFER_LATENCY,
            new ExecutorServiceScheduler(
                    () -> Duration.ZERO,
                    time.scheduledExecutor(Executors.newCachedThreadPool()),
                    time.clock()
            )
    );

    BatchAccumulator.BatchBuilder<String, List<String>> batchBuilder = (input, batch) -> {
      batch.add(input);
      return BatchAccumulator.accepted(batch.size() < 4);
    };

    CompletableFutures.allOf(IntStream.range(0, 4).mapToObj(i -> CompletableFuture.runAsync(() -> {
      for (int j = 0; j < 100; j++) {
        accumulator.accumulate(i + "-" + j, batchBuilder);
      }
    }))).join();

    assertThat(batches).hasSize(100);



  }
}
