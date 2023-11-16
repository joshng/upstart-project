package upstart.aws.kinesis.client;

import com.google.common.base.Ticker;
import com.google.inject.Key;
import com.google.inject.name.Names;
import org.immutables.value.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.cloudwatch.CloudWatchAsyncClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.kinesis.KinesisAsyncClient;
import software.amazon.kinesis.common.ConfigsBuilder;
import software.amazon.kinesis.common.InitialPositionInStream;
import software.amazon.kinesis.common.InitialPositionInStreamExtended;
import software.amazon.kinesis.coordinator.Scheduler;
import software.amazon.kinesis.exceptions.InvalidStateException;
import software.amazon.kinesis.exceptions.ShutdownException;
import software.amazon.kinesis.lifecycle.events.InitializationInput;
import software.amazon.kinesis.lifecycle.events.LeaseLostInput;
import software.amazon.kinesis.lifecycle.events.ProcessRecordsInput;
import software.amazon.kinesis.lifecycle.events.ShardEndedInput;
import software.amazon.kinesis.lifecycle.events.ShutdownRequestedInput;
import software.amazon.kinesis.processor.RecordProcessorCheckpointer;
import software.amazon.kinesis.processor.ShardRecordProcessor;
import software.amazon.kinesis.processor.SingleStreamTracker;
import software.amazon.kinesis.retrieval.KinesisClientRecord;
import software.amazon.kinesis.retrieval.polling.PollingConfig;
import upstart.aws.AwsClientModule;
import upstart.aws.kinesis.KinesisStreamService;
import upstart.config.UpstartModule;
import upstart.guice.PrivateBinding;
import upstart.guice.UpstartPrivateModule;
import upstart.util.LocalHost;
import upstart.util.concurrent.Promise;
import upstart.util.concurrent.Throttler;
import upstart.util.concurrent.resourceguard.BoundedResourceGuard;
import upstart.util.concurrent.resourceguard.SemaphoreResourceGuard;
import upstart.util.concurrent.services.NotifyingService;
import upstart.util.exceptions.Exceptions;
import upstart.util.exceptions.Fallible;
import upstart.util.strings.RandomId;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import java.lang.annotation.Annotation;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import static com.google.common.base.Preconditions.checkState;

public class KinesisClientService extends NotifyingService {
  private final Logger LOG;
  private final KinesisStreamService streamService;
  private final Provider<ShardRecordProcessor> shardProcessorProvider;
  private final DynamoDbAsyncClient dynamoDbClient;
  private final CloudWatchAsyncClient cloudWatchClient;
  private final KinesisClientConfig config;
  private Scheduler scheduler;

  @Inject
  KinesisClientService(
          @PrivateBinding KinesisStreamService streamService,
          @PrivateBinding Provider<ShardRecordProcessor> shardProcessorProvider,
          @PrivateBinding DynamoDbAsyncClient dynamoDbClient,
          @PrivateBinding CloudWatchAsyncClient cloudWatchClient,
          @PrivateBinding ConcurrencyLimiter concurrencyLimiter,
          @PrivateBinding KinesisClientConfig config
  ) {
    this.streamService = streamService;
    this.shardProcessorProvider = shardProcessorProvider;
    this.dynamoDbClient = dynamoDbClient;
    this.cloudWatchClient = cloudWatchClient;
    this.config = config;
    LOG = LoggerFactory.getLogger(KinesisClientService.class.getName() + '.' + config.applicationName());
  }

  @Override
  protected void doStart() {
    KinesisAsyncClient kinesisClient = streamService.client();
    String streamName = streamService.streamName();
    ConfigsBuilder configsBuilder = new ConfigsBuilder(
            streamName,
            config.applicationName(),
            kinesisClient,
            dynamoDbClient,
            cloudWatchClient,
            LocalHost.getLocalHostname() + '_' + RandomId.newRandomId(),
            shardProcessorProvider::get
    );

    InitialPositionInStreamExtended initialPositionInStream = InitialPositionInStreamExtended.newInitialPosition(
            InitialPositionInStream.TRIM_HORIZON);
    scheduler = new Scheduler(
            configsBuilder.checkpointConfig(),
            configsBuilder.coordinatorConfig().workerStateChangeListener(newState -> {
              switch (newState) {
                case STARTED -> notifyStarted();
                case SHUT_DOWN -> notifyStopped();
              }
            }),
            configsBuilder.leaseManagementConfig().initialPositionInStream(initialPositionInStream),
            configsBuilder.lifecycleConfig(),
            configsBuilder.metricsConfig(),
            configsBuilder.processorConfig(),
            configsBuilder.retrievalConfig()
                    .streamTracker(new SingleStreamTracker(streamName, config.initialPositionInStreamExtended()))
                    .retrievalSpecificConfig(new PollingConfig(streamName, kinesisClient))
    );
    new Thread(scheduler, config.applicationName() + "-worker").start();
  }

  @Override
  protected void doStop() {
    if (scheduler != null) scheduler.startGracefulShutdown();
  }

  private void checkpoint(RecordProcessorCheckpointer checkpointer) {
    try {
      if (state() != State.FAILED) checkpointer.checkpoint();
    } catch (InvalidStateException | ShutdownException e) {
      notifyFailed(e);
    }
  }
  private void checkpoint(RecordProcessorCheckpointer checkpointer, KinesisClientRecord record) {
    try {
      if (state() != State.FAILED) checkpointer.checkpoint(record.sequenceNumber(), record.subSequenceNumber());
    } catch (InvalidStateException | ShutdownException e) {
      notifyFailed(e);
    }
  }

  static class ShardProcessor implements ShardRecordProcessor {
    private final KinesisClientService service;
    private final KinesisShardProcessor processor;
    private final Throttler checkpointThrottler;
    private final KinesisClientConfig config;
    private final Logger LOG;
    private final AtomicBoolean healthy = new AtomicBoolean(true);

    private volatile Promise<Void> prevBatchCompletion = Promise.nullPromise();
    private final ConcurrencyLimiter concurrencyLimiter;


    @Inject
    ShardProcessor(
            KinesisClientService service,
            KinesisShardProcessor processor,
            ConcurrencyLimiter concurrencyLimiter,
            Ticker ticker
    ) {
      this.service = service;
      config = service.config;
      this.processor = processor;
      this.checkpointThrottler = new Throttler(config.checkpointFrequency(), ticker);
      LOG = service.LOG;
      this.concurrencyLimiter = concurrencyLimiter;
    }

    @Override
    public void initialize(InitializationInput initializationInput) {
      LOG.info(
              "Initializing shard {} at offset {}",
              initializationInput.shardId(),
              initializationInput.extendedSequenceNumber()
      );
      stopOnError(() -> processor.initialize(initializationInput));
    }

    @Override
    public void processRecords(ProcessRecordsInput input) {
      if (!healthy.get()) return;
      LOG.debug("Processing records: {}, {}s behind latest", input.records().size(), input.millisBehindLatest() / 1000);
      stopOnError(() -> {
        var prevCompletion = prevBatchCompletion;
        for (KinesisClientRecord record : input.records()) {
          Promise<Void> recordCompletion = concurrencyLimiter
                  .completeWithResource(() -> Promise
                          .<Void>thatCompletes(promise -> processor.processRecord(
                                  new KinesisInputRecord(record, input, promise)
                          ))
                  ).uponFailure(e -> notifyFailed("Error processing record", e));
          prevCompletion = prevCompletion.thenCombine(recordCompletion, (a, b) -> null);
          prevCompletion.thenRun(() -> {
            if (healthy.get() && checkpointThrottler.tryAcquire()) service.checkpoint(input.checkpointer(), record);
          });
        }

        this.prevBatchCompletion = prevCompletion;
      });
    }

    private void notifyFailed(String message, Throwable e) {
      if (markUnhealthy()) {
        LOG.error(message, e);
        service.notifyFailed(e);
      }
    }

    @Override
    public void leaseLost(LeaseLostInput leaseLostInput) {
      markUnhealthy();
      processor.leaseLost();
    }

    @Override
    public void shardEnded(ShardEndedInput shardEndedInput) {
      // KCL doesn't allow proceeding to the next shard until the current shard is checkpointed.
      // need to forcibly checkpoint here, because the final record checkpoint was likely throttled
      checkpointLatest(shardEndedInput.checkpointer());
    }

    @Override
    public void shutdownRequested(ShutdownRequestedInput shutdownRequestedInput) {
      stopOnError(() -> {
        processor.shutdownRequested();
        checkpointLatest(shutdownRequestedInput.checkpointer());
        service.stop(); // is this necessary? shouldn't hurt
      });
    }

    private void checkpointLatest(RecordProcessorCheckpointer checkpointer) {
      prevBatchCompletion.join();
      if (healthy.get()) {
        checkpointThrottler.forceAcquire();
        service.checkpoint(checkpointer);
      }
    }

    private boolean markUnhealthy() {
      return healthy.compareAndSet(true, false);
    }

    private <E extends Exception> void stopOnError(Fallible<E> action) {
      try {
        action.run();
      } catch (Throwable e) {
        notifyFailed("Shard processor failed", e);
        throw Exceptions.throwUnchecked(e);
      }
    }
  }


  @Value.Immutable
  public abstract static class KinesisClientModule extends UpstartModule {
    public static Builder builder() {
      return new Builder();
    }

    @Value.Default
    public Key<KinesisClientService> clientServiceKey() {
      return Key.get(KinesisClientService.class);
    }


    public abstract Key<? extends KinesisClientConfig> configKey();

    public abstract Key<? extends KinesisStreamService> streamKey();

    public abstract Key<? extends ShardRecordProcessor> shardRecordProcessorKey();

    public abstract Optional<Key<? extends DynamoDbAsyncClient>> dynamoDbClientKey();

    public abstract Optional<Key<? extends CloudWatchAsyncClient>> cloudWatchClientKey();

    abstract List<com.google.inject.Module> extraModules();

    @Override
    protected void configure() {
      Key<? extends DynamoDbAsyncClient> dynamoKey = dynamoDbClientKey().orElseGet(() -> {
        AwsClientModule.installWithDefaultConfig(binder(), DynamoDbAsyncClient.class);
        return Key.get(DynamoDbAsyncClient.class);
      });

      Key<? extends CloudWatchAsyncClient> cloudWatchKey = cloudWatchClientKey().orElseGet(() -> {
        AwsClientModule.installWithDefaultConfig(binder(), CloudWatchAsyncClient.class);
        return Key.get(CloudWatchAsyncClient.class);
      });

      extraModules().forEach(this::install);

      install(new UpstartPrivateModule() {
        @Override
        protected void configure() {
          bindPrivateBinding(KinesisStreamService.class).to(streamKey());
          bindPrivateBinding(DynamoDbAsyncClient.class).to(dynamoKey);
          bindPrivateBinding(CloudWatchAsyncClient.class).to(cloudWatchKey);
          bindPrivateBinding(KinesisClientConfig.class).to(configKey());
          bindPrivateBinding(ShardRecordProcessor.class).to(shardRecordProcessorKey());
          if (clientServiceKey().getAnnotationType() != null) {
            bind(clientServiceKey()).to(clientServiceKey().getTypeLiteral()).asEagerSingleton();
          } else {
            bind(clientServiceKey()).asEagerSingleton();
          }
          expose(clientServiceKey());
        }
      });

      serviceManager().manage(clientServiceKey());
    }

    @Override
    public abstract boolean equals(Object obj);

    @Override
    public abstract int hashCode();

    public static class Builder extends ImmutableKinesisClientModule.Builder {
      public Builder streamConfig(KinesisStreamService.StreamConfig streamConfig) {
        return streamConfig(Names.named(streamConfig.name()), streamConfig);
      }

      public Builder streamConfig(Annotation annotation, KinesisStreamService.StreamConfig streamConfig) {
        KinesisStreamService.Module streamModule = new KinesisStreamService.Module(annotation, streamConfig);
        addExtraModules(streamModule);
        streamKey(Key.get(KinesisStreamService.class, annotation));
        return this;
      }
    }
  }

  @Singleton
  private static class ConcurrencyLimiter {
    private final BoundedResourceGuard<?> guard;

    @Inject
    public ConcurrencyLimiter(KinesisClientConfig config) {
      this.guard = new SemaphoreResourceGuard(config.maxInFlightRecords());
    }

    public <O> Promise<O> completeWithResource(Supplier<? extends Promise<O>> job) {
      return guard.completeWithResource(job);
    }
  }


  public interface KinesisClientConfig {
    static ImmutableDefaultKinesisClientConfig.Builder builder() {
      return ImmutableDefaultKinesisClientConfig.builder();
    }

    String applicationName();

    InitialPositionInStream initialPositionInStream();

    Optional<Instant> initialTimestamp();

    Duration checkpointFrequency();

    int maxInFlightRecords();

    default InitialPositionInStreamExtended initialPositionInStreamExtended() {
      return initialTimestamp().map(
              timestamp -> InitialPositionInStreamExtended.newInitialPositionAtTimestamp(new Date(timestamp.toEpochMilli()))
      ).orElseGet(() -> InitialPositionInStreamExtended.newInitialPosition(initialPositionInStream()));
    }

    @Value.Immutable
    interface DefaultKinesisClientConfig extends KinesisClientConfig {


    }
  }
}
