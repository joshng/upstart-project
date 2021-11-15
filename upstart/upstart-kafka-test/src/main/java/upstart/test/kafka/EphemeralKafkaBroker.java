package upstart.test.kafka;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import upstart.test.SingletonExtension;
import kafka.server.KafkaConfig;
import kafka.server.KafkaServer;
import org.apache.curator.test.InstanceSpec;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serializer;
import org.apache.kafka.common.utils.Time;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scala.Option;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;

/**
 * Ripped from: https://github.com/charithe/kafka-junit
 */

public class EphemeralKafkaBroker {
  private static final Logger LOGGER = LoggerFactory.getLogger(EphemeralKafkaBroker.class);
  private static final int ALLOCATE_RANDOM_PORT = -1;
  private static final String LOCALHOST = "localhost";

  private int kafkaPort;
  private final String zookeeperConnectString;
  private Properties overrideBrokerProperties;

  private KafkaServer kafkaServer;
  private Path kafkaLogDir;

  private volatile CompletableFuture<Void> startupFuture = null;

  public static EphemeralKafkaBroker getInstance(ExtensionContext context) {
    return SingletonExtension.getRequiredContextFrom(KafkaExtension.class, context);
  }

  /**
   * Create a new ephemeral Kafka broker with random broker port and Zookeeper port
   *
   * @return EphemeralKafkaBroker
   */
  public static EphemeralKafkaBroker create(String zookeeperConnectString) {
    return create(ALLOCATE_RANDOM_PORT, zookeeperConnectString);
  }

  /**
   * Create a new ephemeral Kafka broker with the specified broker port and random Zookeeper port
   *
   * @param kafkaPort Port the broker should listen on
   * @return EphemeralKafkaBroker
   */
  public static EphemeralKafkaBroker create(int kafkaPort, String zookeeperConnectString) {
    return create(kafkaPort, zookeeperConnectString, new Properties());
  }

  /**
   * Create a new ephemeral Kafka broker with the specified broker port, Zookeeper port and config overrides.
   *
   * @param kafkaPort                Port the broker should listen on
   * @param overrideBrokerProperties Broker properties to override. Pass null if there aren't any.
   * @return EphemeralKafkaBroker
   */
  public static EphemeralKafkaBroker create(int kafkaPort, String zookeeperConnectString, Properties overrideBrokerProperties) {
    return new EphemeralKafkaBroker(kafkaPort, zookeeperConnectString, overrideBrokerProperties);
  }


  EphemeralKafkaBroker(int kafkaPort, String zookeeperConnectString, Properties overrideBrokerProperties) {
    this.kafkaPort = kafkaPort;
    this.zookeeperConnectString = zookeeperConnectString;
    this.overrideBrokerProperties = overrideBrokerProperties;
  }

  public static NewTopic newTopic(String name, int partitions, TopicConfig... configs) {
    TopicAdmin.NewTopicBuilder builder = topicBuilder(name, partitions);
    for (TopicConfig config : configs) {
      config.accept(builder);
    }
    return builder.build();
  }

  public static TopicAdmin.NewTopicBuilder topicBuilder(String name, int partitions) {
    return TopicAdmin.defineTopic(name)
            .partitions(partitions)
            .minInSyncReplicas((short)1)
            .replicationFactor((short)1);
  }

  /**
   * Start the Kafka broker
   */
  public CompletableFuture<Void> start() throws Exception {
    synchronized (this) {
      if (startupFuture == null) {
        startupFuture = startBroker();
      }
    }

    return startupFuture;
  }

  private CompletableFuture<Void> startBroker() throws Exception {
    kafkaPort = kafkaPort == ALLOCATE_RANDOM_PORT ? InstanceSpec.getRandomPort() : kafkaPort;
    KafkaConfig kafkaConfig = buildKafkaConfig(zookeeperConnectString);

    LOGGER.info("Starting Kafka server with config: {}", kafkaConfig.props());
    //fails with error:  incompatible types: scala.collection.Seq<kafka.metrics.KafkaMetricsReporter> cannot be converted to boolean
    //kafkaServer = new KafkaServer(kafkaConfig, Time.SYSTEM, Option.<String>empty(), KafkaMetricsReporter.startReporters(new VerifiableProperties(new Properties())));
    kafkaServer = new KafkaServer(kafkaConfig, Time.SYSTEM, Option.<String>empty(), false);
    return CompletableFuture.runAsync(() -> kafkaServer.startup());
  }

  public void createTopics(NewTopic... topics) {
    try (AdminClient adminClient = AdminClient.create(ImmutableMap.of("bootstrap.servers", getBrokerList().get()))) {
      adminClient.createTopics(Arrays.asList(topics));
    }
  }

  /**
   * Stop the Kafka broker
   */
  public void stop() {
    synchronized (this) {
      if (isRunning()) {
        startupFuture = null;
        stopBroker();
      }
    }
  }

  private void stopBroker() {
    try {
      if (kafkaServer != null) {
        LOGGER.info("Shutting down Kafka Server");
        kafkaServer.shutdown();
      }

      if (Files.exists(kafkaLogDir)) {
        LOGGER.info("Deleting the log dir:  {}", kafkaLogDir);
        Files.walkFileTree(kafkaLogDir, new SimpleFileVisitor<Path>() {
          @Override
          public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
            Files.deleteIfExists(file);
            return FileVisitResult.CONTINUE;
          }

          @Override
          public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
            Files.deleteIfExists(dir);
            return FileVisitResult.CONTINUE;
          }
        });
      }
    } catch (Exception e) {
      LOGGER.error("Failed to clean-up Kafka", e);
    }
  }

  private KafkaConfig buildKafkaConfig(String zookeeperQuorum) throws IOException {
    kafkaLogDir = Files.createTempDirectory("kafka_junit");

    Properties props = new Properties();
    props.put("advertised.listeners", "PLAINTEXT://" + LOCALHOST + ":" + kafkaPort);
    props.put("listeners", "PLAINTEXT://0.0.0.0:" + kafkaPort);
    props.put("port", kafkaPort + "");
    props.put("broker.id", "1");
    props.put("log.dirs", kafkaLogDir.toAbsolutePath().toString());
    props.put("zookeeper.connect", zookeeperQuorum);
    props.put("leader.imbalance.check.interval.seconds", "1");
    props.put("offsets.topic.num.partitions", "1");
    props.put("offsets.topic.replication.factor", "1");
    props.put("transaction.state.log.replication.factor", "1");
    props.put("transaction.state.log.min.isr", "1");
    props.put("min.insync.replicas", "1");
    props.put("default.replication.factor", "1");
    props.put("num.partitions", "1");
    props.put("group.min.session.timeout.ms", "100");

    if (overrideBrokerProperties != null) {
      props.putAll(overrideBrokerProperties);
    }

    return new KafkaConfig(props);
  }

  /**
   * Create a minimal producer configuration that can be used to produce to this broker
   *
   * @return Properties
   */
  public Properties producerConfig() {
    Properties props = new Properties();
    props.put("bootstrap.servers", LOCALHOST + ":" + kafkaPort);
    props.put("acks", "1");
    props.put("batch.size", "10");
    props.put("client.id", "kafka-junit");
    props.put("request.timeout.ms", "500");

    return props;
  }

  /**
   * Create a minimal consumer configuration with auto commit enabled. Offset is set to "earliest".
   *
   * @return Properies
   */
  public Properties consumerConfig() {
    return consumerConfig(false);
  }

  /**
   * Create a minimal consumer configuration. Offset is set to "earliest".
   *
   * @return Properties
   */
  public Properties consumerConfig(boolean enableAutoCommit) {
    Properties props = new Properties();
    props.put("bootstrap.servers", LOCALHOST + ":" + kafkaPort);
    props.put("group.id", "kafka-junit-consumer");
    props.put("enable.auto.commit", String.valueOf(enableAutoCommit));
    props.put("auto.commit.interval.ms", "10");
    props.put("auto.offset.reset", "earliest");
    props.put("heartbeat.interval.ms", "100");
    props.put("session.timeout.ms", "200");
    props.put("fetch.max.wait.ms", "200");
    props.put("metadata.max.age.ms", "100");

    return props;
  }

  /**
   * Create a producer that can write to this broker
   *
   * @param keySerializer   Key serializer class
   * @param valueSerializer Valuer serializer class
   * @param overrideConfig  Producer config to override. Pass null if there aren't any.
   * @param <K>             Type of Key
   * @param <V>             Type of Value
   * @return KafkaProducer
   */
  public <K, V> KafkaProducer<K, V> createProducer(Serializer<K> keySerializer, Serializer<V> valueSerializer,
                                                   Properties overrideConfig) {
    Properties conf = producerConfig();
    if (overrideConfig != null) {
      conf.putAll(overrideConfig);
    }
    keySerializer.configure(Maps.fromProperties(conf), true);
    valueSerializer.configure(Maps.fromProperties(conf), false);
    return new KafkaProducer<>(conf, keySerializer, valueSerializer);
  }

  public KafkaProducer<byte[], byte[]> createByteProducer() {
    ByteArraySerializer serializer = new ByteArraySerializer();
    return createProducer(serializer, serializer, null);
  }

  /**
   * Create a consumer that can read from this broker
   *
   * @param keyDeserializer   Key deserializer
   * @param valueDeserializer Value deserializer
   * @param overrideConfig    Consumer config to override. Pass null if there aren't any
   * @param <K>               Type of Key
   * @param <V>               Type of Value
   * @return KafkaConsumer
   */
  public <K, V> KafkaConsumer<K, V> createConsumer(Deserializer<K> keyDeserializer, Deserializer<V> valueDeserializer,
                                                   Properties overrideConfig) {
    Properties conf = consumerConfig();
    if (overrideConfig != null) {
      conf.putAll(overrideConfig);
    }
    keyDeserializer.configure(Maps.fromProperties(conf), true);
    valueDeserializer.configure(Maps.fromProperties(conf), false);
    return new KafkaConsumer<>(conf, keyDeserializer, valueDeserializer);
  }

  public KafkaConsumer<byte[], byte[]> createByteConsumer() {
    return createByteConsumer(null);
  }

  public KafkaConsumer<byte[], byte[]> createByteConsumer(Map<String, String> config) {
    ByteArrayDeserializer serializer = new ByteArrayDeserializer();
    Properties props = new Properties();
    props.putAll(config);
    return createConsumer(serializer, serializer, props);
  }

  /**
   * Get the broker port
   *
   * @return An optional that will only contain a value if the broker is running
   */
  public Optional<Integer> getKafkaPort() {
    return isRunning() ? Optional.of(kafkaPort) : Optional.empty();
  }

  /**
   * Get the path to the Kafka log directory
   *
   * @return An Optional that will only contain a value if the broker is running
   */
  public Optional<String> getLogDir() {
    return isRunning() ? Optional.of(kafkaLogDir.toString()) : Optional.empty();
  }

  /**
   * Get the current broker list string
   *
   * @return An Optional that will only contain a value if the broker is running
   */
  public Optional<String> getBrokerList() {
    return isRunning() ? Optional.of(LOCALHOST + ":" + kafkaPort) : Optional.empty();
  }

  /**
   * Is the broker running?
   *
   * @return True if the broker is running
   */
  public boolean isRunning() {
    return startupFuture != null;
  }

}
