package io.upstartproject.avrocodec.kafka;

import io.upstartproject.avrocodec.AvroPublisher;
import io.upstartproject.avrocodec.BaseSchemaRegistry;
import io.upstartproject.avrocodec.SchemaDescriptor;
import io.upstartproject.avrocodec.SchemaRegistry;
import upstart.util.concurrent.CompletableFutures;
import upstart.util.concurrent.Promise;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.LongDeserializer;
import org.apache.kafka.common.serialization.LongSerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.kafka.common.utils.SystemTime;

import javax.inject.Inject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * A {@link SchemaRegistry} implementation, for use with an {@link AvroPublisher#AvroCodec(SchemaRegistry) AvroCodec}, which
 * stores schemas in a configured Kafka topic.
 *
 * @see SchemaRegistry
 * @see AvroPublisher#AvroCodec(SchemaRegistry)
 */
public class KafkaSchemaRegistry extends BaseSchemaRegistry {
  private final String topic;
  private final Config config;

  private KafkaBasedLog<Long, String> kafkaLog;
  private Producer<Long, String> producer;

  @Inject
  public KafkaSchemaRegistry(Config config) {
    this.config = config;
    this.topic = config.schemaTopic();
  }

  @Override
  protected CompletableFuture<?> startUpAsync() {
    return runOnDedicatedThread("STARTING", () -> {
      Map<String, Object> consumerProps = new HashMap<>(config.consumerConfigs());
      consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, config.brokerList());
      consumerProps.put(ConsumerConfig.CLIENT_ID_CONFIG, config.clientId());
      consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, LongDeserializer.class.getName());
      consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
      this.kafkaLog = new KafkaBasedLog<>(topic, consumerProps, this::ingestSchema, new SystemTime());
      kafkaLog.start();

      Map<String, Object> producerProps = new HashMap<>(config.producerConfigs());
      producerProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, config.brokerList());
      producerProps.put(ProducerConfig.CLIENT_ID_CONFIG, config.clientId());
      producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, LongSerializer.class);
      producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
      // all of our messages are idempotent, so retries are fine
      producerProps.put(ProducerConfig.RETRIES_CONFIG, Integer.MAX_VALUE);

      // Always require producer acks to all to ensure durable writes
      producerProps.put(ProducerConfig.ACKS_CONFIG, "all");

      // Don't allow more than one in-flight request to prevent reordering on retry
      producerProps.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 1);

      producer = new KafkaProducer<>(producerProps);
    });
  }

  @Override
  public CompletableFuture<?> shutDown() {
    return runOnDedicatedThread("STOPPING", () -> {
      producer.close();
      kafkaLog.stop();
    });
  }

  private void ingestSchema(ConsumerRecord<Long, String> record) {
    String schemaJson = record.value();
    if (schemaJson != null) {
      notifySchemaAdded(schemaJson);
    } else {
      // tombstone record: schema was deleted, probably retracted by its publisher
      notifySchemaRemoved(record.key());
    }
  }

  @Override
  public CompletableFuture<Void> insert(List<? extends SchemaDescriptor> newSchemas) {
    return CompletableFutures.allOf(newSchemas.stream()
            .map(newSchema -> Promise.thatCompletes(promise ->
                    producer.send(new ProducerRecord<>(topic, newSchema.fingerprint().value(), newSchema.schema().toString()), promise::accept))
            )
    );
  }

  @Override
  public CompletableFuture<?> delete(SchemaDescriptor schema) {
    return Promise.thatCompletes(promise ->
            producer.send(new ProducerRecord<>(topic, schema.fingerprint().value(), null), promise::accept));
  }

  @Override
  public CompletableFuture<Void> refresh() {
    return kafkaLog.readToEnd();
  }

  public interface Config {
    String schemaTopic();
    String brokerList();
    String clientId();
    Map<String, String> producerConfigs();
    Map<String, String> consumerConfigs();
  }
}
