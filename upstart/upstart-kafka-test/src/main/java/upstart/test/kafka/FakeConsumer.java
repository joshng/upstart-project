package upstart.test.kafka;

import com.google.common.collect.Sets;
import org.apache.kafka.clients.consumer.ConsumerRebalanceListener;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.MockConsumer;
import org.apache.kafka.clients.consumer.OffsetResetStrategy;
import org.apache.kafka.common.TopicPartition;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public class FakeConsumer extends MockConsumer<byte[], byte[]> {
  private final AtomicReference<Set<TopicPartition>> currentAssignment = new AtomicReference<>(Collections.emptySet());
  private final ThreadLocal<Boolean> pollInProgress = ThreadLocal.withInitial(() -> false);
  private final Map<TopicPartition, AtomicLong> endOffsets = new ConcurrentHashMap<>();

  @Nullable
  private ConsumerRebalanceListener rebalanceListener = null;

  @Inject
  public FakeConsumer(OffsetResetStrategy offsetResetStrategy) {
    super(offsetResetStrategy);
  }

  @Override
  public synchronized void subscribe(Pattern pattern, ConsumerRebalanceListener listener) {
    rebalanceListener = listener;
    super.subscribe(pattern, listener);
  }

  @Override
  public synchronized void subscribe(Collection<String> topics, ConsumerRebalanceListener listener) {
    rebalanceListener = listener;
    super.subscribe(topics, listener);
  }

  @Override
  public ConsumerRecords<byte[], byte[]> poll(long timeout) {
    Boolean wasPolling = pollInProgress.get();
    pollInProgress.set(true);
    try {
      return super.poll(timeout);
    } finally {
      pollInProgress.set(wasPolling);
    }
  }

  public TopicPartition[] initEmptyTopic(String topic, int partitions) {
    checkArgument(partitions > 0, "partitions must be positive");
    TopicPartition[] partitionArray = new TopicPartition[partitions];
    for (int i = 0; i < partitions; i++) {
      TopicPartition tp = new TopicPartition(topic, i);
      initEmptyPartition(tp);
      partitionArray[i] = tp;
    }
    return partitionArray;
  }

  public void initEmptyPartition(TopicPartition tp) {
    setInitialOffsets(tp, 0, 0);
  }

  /**
   * Initializes begin/end offsets for the given partition
   * @see #initEmptyTopic
   * @see #initEmptyPartition
   */
  public void setInitialOffsets(TopicPartition tp, long beginOffset, long endOffset) {
    updateBeginningOffsets(Collections.singletonMap(tp, beginOffset));
    updateEndOffsets(Collections.singletonMap(tp, endOffset));
  }


  @Override
  public synchronized void updateEndOffsets(Map<TopicPartition, Long> newOffsets) {
    super.updateEndOffsets(newOffsets);
    newOffsets.forEach((tp, offset) -> endOffsets.computeIfAbsent(tp, k -> new AtomicLong()).set(offset));
  }

  public void send(String topic, int partition, byte[] key, byte[] value) {
    send(new TopicPartition(topic, partition), key, value);
  }

  public void send(TopicPartition tp, byte[] key, byte[] value) {
    invokeWithPoll(() -> {
      long offset = checkNotNull(endOffsets.get(tp), "End-offset not initialized (see FakeConsumer.setInitialOffsets): %s", tp)
              .incrementAndGet();
      addRecord(new ConsumerRecord<>(tp.topic(), tp.partition(), offset, key, value));
    });
  }

  public void rebalanceWithCallbacks(Collection<TopicPartition> newAssignment) {
    invokeWithPoll(() -> {
      rebalance(newAssignment);
      Set<TopicPartition> result = assignment();
      Set<TopicPartition> prevAssignment = currentAssignment.getAndSet(result);
      // the kafka MockConsumer should really call these callbacks for us as part of rebalance, but it's just noted as
      // a TO-DO in that method (as of kafka-clients:2.6.0, anyway)!
      if (rebalanceListener != null) {
        rebalanceListener.onPartitionsRevoked(Sets.difference(prevAssignment, result));
        rebalanceListener.onPartitionsAssigned(Sets.difference(result, prevAssignment));
      }
    });
  }

  public void waitForNextPoll() throws InterruptedException {
    CountDownLatch latch = new CountDownLatch(1);
    schedulePollTask(latch::countDown);
    latch.await();
  }

  private void invokeWithPoll(Runnable task) {
    if (pollInProgress.get()) {
      task.run();
    } else {
      schedulePollTask(task);
    }
  }
}
