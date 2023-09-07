package io.upstartproject.avrocodec.kafka;

/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import com.google.common.base.MoreObjects;
import upstart.util.concurrent.Promise;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.KafkaException;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.WakeupException;
import org.apache.kafka.common.utils.Time;
import org.apache.kafka.common.utils.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;


/**
 * <p>
 * KafkaBasedLog provides a generic implementation of a shared, compacted log of records stored in Kafka that all
 * clients need to consume and, at times, agree on their offset / that they have read to the end of the log.
 * </p>
 * <p>
 * This functionality is useful for storing different types of data that all clients may need to agree on --
 * offsets or config for example. This class runs a consumer in a background thread to continuously tail the target
 * topic and provides some helpful utilities like checking the current log end offset and waiting until the current
 * end of the log is reached.
 * </p>
 * <p>
 * To support different use cases, this class works with either single- or multi-partition topics.
 * </p>
 * <p>
 * Since this class is generic, it delegates the details of data storage via a callback that is invoked for each
 * record that is consumed from the topic. The invocation of callbacks is guaranteed to be serialized -- if the
 * calling class keeps track of state based on the log and only writes to it when consume callbacks are invoked
 * and only reads it in {@link #readToEnd} callbacks then no additional synchronization will be required.
 * </p>
 */
public class KafkaBasedLog<K, V> {
  private static final Logger log = LoggerFactory.getLogger(KafkaBasedLog.class);
  private static final long CREATE_TOPIC_TIMEOUT_MS = 30000;

  private Time time;
  private final String topic;
  private final Map<String, Object> consumerConfigs;
  private final java.util.function.Consumer<ConsumerRecord<K, V>> consumedCallback;
  private Consumer<K, V> consumer;

  private Thread thread;
  private boolean stopRequested;
  private Queue<BiConsumer<? super Void, ? super Exception>> readLogEndOffsetCallbacks;
  private Exception failureException;

  /**
   * Create a new KafkaBasedLog object. This does not start reading the log and writing is not permitted until
   * {@link #start()} is invoked.
   *
   * @param topic            the topic to treat as a log
   * @param consumerConfigs  configuration options to use when creating the internal consumer. At a minimum this must
   *                         contain compatible serializer settings for the generic types used on this class. Some
   *                         setting, such as the auto offset reset policy, will be overridden to ensure correct
   *                         behavior of this class.
   * @param consumedCallback callback to invoke for each {@link ConsumerRecord} consumed when tailing the log
   * @param time             Time interface
   */
  public KafkaBasedLog(String topic,
                       Map<String, Object> consumerConfigs,
                       java.util.function.Consumer<ConsumerRecord<K, V>> consumedCallback,
                       Time time) {
    this.topic = topic;
    this.consumerConfigs = consumerConfigs;
    this.consumedCallback = consumedCallback;
    this.stopRequested = false;
    this.readLogEndOffsetCallbacks = new ArrayDeque<>();
    this.time = time;
  }

  public void start() {
    log.info("Starting KafkaBasedLog with topic {}", topic);

    consumer = createConsumer();

    // Until we have admin utilities we can use to check for the existence of this topic and create it if it is missing,
    // we rely on topic auto-creation
    long started = time.milliseconds();
    List<PartitionInfo> partitionInfos = consumer.partitionsFor(topic);
    while (partitionInfos == null && time.milliseconds() - started < CREATE_TOPIC_TIMEOUT_MS) {
      Utils.sleep(Math.min(time.milliseconds() - started, 1000));
      partitionInfos = consumer.partitionsFor(topic);
    }
    if (partitionInfos == null)
      throw new RuntimeException("Could not look up partition metadata for offset backing store topic in" +
              " allotted period. This could indicate a connectivity issue, unavailable topic partitions, or if" +
              " this is your first use of the topic it may have taken too long to create.");

    List<TopicPartition> topicPartitions = partitionInfos.stream()
            .map(partition -> new TopicPartition(partition.topic(), partition.partition()))
            .collect(Collectors.toList());

    consumer.assign(topicPartitions);
    consumer.seekToBeginning(topicPartitions);

    readToLogEnd();

    thread = new WorkThread();
    thread.start();

    log.info("KafkaBasedLog initialized for topic {}", topic);
  }

  public void stop() {
    log.info("Stopping KafkaBasedLog for topic {}", topic);

    synchronized (this) {
      stopRequested = true;
    }
    consumer.wakeup();

    try {
      thread.join();
    } catch (InterruptedException e) {
      throw new RuntimeException("Failed to stop KafkaBasedLog. Exiting without cleanly shutting " +
              "down it's consumer.", e);
    }

    try {
      consumer.close();
    } catch (KafkaException e) {
      log.error("Failed to stop KafkaBasedLog consumer", e);
    }

    log.info("Stopped KafkaBasedLog for topic " + topic);
  }

  /**
   * Reads to the current end of the log and invokes the specified callback.
   * Note that this checks the current offsets, reads to them, and invokes the callback regardless of whether
   * additional records have been written to the log. If the caller needs to ensure they have truly reached the end
   * of the log, they must ensure there are no other writers during this period.
   * <p>
   * This waits until the end of all partitions has been reached.
   *
   * @param callback the callback to invoke once the end of the log has been reached.
   */
  public void readToEnd(BiConsumer<? super Void, ? super Exception> callback) {
    log.trace("Starting read to end log for topic {}", topic);
    Exception failure;
    synchronized (this) {
      failure = failureException;
      if (failure == null) readLogEndOffsetCallbacks.add(callback);
    }
    if (failure == null) {
      consumer.wakeup();
    } else {
      callback.accept(null, failure);
    }
  }

  /**
   * Same as {@link #readToEnd} but provides a {@link Future} instead of using a callback.
   *
   * @return the future associated with the operation
   */
  public Promise<Void> readToEnd() {
    return Promise.thatCompletes(this::readToEnd);
  }

  private Consumer<K, V> createConsumer() {
    // Turn off autocommit since we always want to consume the full log
    consumerConfigs.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
    return new KafkaConsumer<>(consumerConfigs);
  }

  private void poll() {
    try {
      ConsumerRecords<K, V> records = consumer.poll(Long.MAX_VALUE);
      for (ConsumerRecord<K, V> record : records) {
        consumedCallback.accept(record);
      }
    } catch (WakeupException e) {
      // Expected on get() or stop(). The calling code should handle this
      throw e;
    } catch (KafkaException e) {
      log.error("Error polling", e);
    }
  }

  private void readToLogEnd() {
    log.trace("Reading to end of offset log");

    Set<TopicPartition> assignment = consumer.assignment();
    Map<TopicPartition, Long> endOffsets = consumer.endOffsets(assignment);

    log.trace("Reading to end of log offsets {}", endOffsets);

    while (!endOffsets.isEmpty()) {
      Iterator<Map.Entry<TopicPartition, Long>> it = endOffsets.entrySet().iterator();
      while (it.hasNext()) {
        Map.Entry<TopicPartition, Long> entry = it.next();
        if (consumer.position(entry.getKey()) >= entry.getValue())
          it.remove();
        else {
          poll();
          break;
        }
      }
    }
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(KafkaBasedLog.class)
            .add("topic", topic)
            .add("consumer", consumerConfigs)
            .toString();
  }

  private class WorkThread extends Thread {
    WorkThread() {
      super("KafkaBasedLog-" + topic);
    }

    @Override
    public void run() {
      try {
        log.trace("{} started execution", this);
        while (true) {
          int numCallbacks;
          synchronized (KafkaBasedLog.this) {
            if (stopRequested)
              break;
            numCallbacks = readLogEndOffsetCallbacks.size();
          }

          if (numCallbacks > 0) {
            try {
              readToLogEnd();
              log.trace("Finished read to end log for topic {}", topic);
            } catch (WakeupException e) {
              // Either received another get() call and need to retry reading to end of log or stop() was
              // called. Both are handled by restarting this loop.
              continue;
            }
          }

          synchronized (KafkaBasedLog.this) {
            // Only invoke exactly the number of callbacks we found before triggering the read to log end
            // since it is possible for another write + readToEnd to sneak in in the meantime
            for (int i = 0; i < numCallbacks; i++) {
              BiConsumer<? super Void, ? super Exception> cb = readLogEndOffsetCallbacks.poll();
              assert cb != null;
              cb.accept(null, null);
            }
          }

          try {
            poll();
          } catch (WakeupException e) {
            // See previous comment, both possible causes of this wakeup are handled by starting this loop again
            continue;
          }
        }
      } catch (Throwable t) {
        log.error("Unexpected exception in WorkerThread, LOG IS PERMANENTLY STALLED!: {}", KafkaBasedLog.this, t);
        synchronized (KafkaBasedLog.this) {
          failureException = t instanceof Exception ? (Exception) t : new RuntimeException(t);
          readLogEndOffsetCallbacks.forEach(cb -> cb.accept(null, failureException));
        }

      }
    }
  }
}
