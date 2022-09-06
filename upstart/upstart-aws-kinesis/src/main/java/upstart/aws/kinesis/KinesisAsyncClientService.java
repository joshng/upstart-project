package upstart.aws.kinesis;

import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.kinesis.KinesisAsyncClient;
import software.amazon.awssdk.services.kinesis.KinesisAsyncClientBuilder;
import software.amazon.awssdk.services.kinesis.model.PutRecordResponse;
import software.amazon.awssdk.services.kinesis.model.ResourceInUseException;
import upstart.aws.Aws;
import upstart.aws.AwsAsyncClientFactory;
import upstart.aws.BaseAwsAsyncClientService;
import upstart.config.UpstartModule;
import upstart.managedservices.ServiceLifecycle;
import upstart.util.concurrent.NamedThreadFactory;
import upstart.util.concurrent.Promise;
import upstart.util.concurrent.services.ThreadPoolService;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class KinesisAsyncClientService extends BaseAwsAsyncClientService<KinesisAsyncClient, KinesisAsyncClientBuilder> {

  @Inject
  public KinesisAsyncClientService(
          @Aws(Aws.Service.Kinesis) AwsAsyncClientFactory clientFactory,
          KinesisThreadPoolService threadPool
  ) {
    super(clientFactory, threadPool);
  }

  @Override
  protected KinesisAsyncClientBuilder asyncClientBuilder() {
    return KinesisAsyncClient.builder();
  }

  public Promise<KinesisStreamPublisher> ensureStreamCreated(String streamName, int shardCount) {
    return Promise.of(client().createStream(b -> b.streamName(streamName).shardCount(shardCount)))
            .recover(ResourceInUseException.class, e -> null)
             // could still fail due to LimitExceededException, or other conditions? probably ok, supervisor will respawn
            .thenReplaceFuture(() -> client().waiter().waitUntilStreamExists(b -> b.streamName(streamName)))
            .thenReplace(new KinesisStreamPublisher(streamName));
  }

  public class KinesisStreamPublisher {
    private final String streamName;

    private KinesisStreamPublisher(String streamName) {
      this.streamName = streamName;
    }

    public Promise<PutRecordResponse> send(String partitionKey, byte[] bytes) {
      return send(partitionKey, SdkBytes.fromByteArray(bytes));
    }

    public Promise<PutRecordResponse> send(String partitionKey, SdkBytes bytes) {
      return Promise.of(client().putRecord(b -> b.streamName(streamName).partitionKey(partitionKey).data(bytes)));
    }
  }

  @Singleton
  @ServiceLifecycle(ServiceLifecycle.Phase.Infrastructure)
  static class KinesisThreadPoolService extends ThreadPoolService {

    protected KinesisThreadPoolService() {
      super(Duration.ofSeconds(5));
    }

    @Override
    protected ExecutorService buildExecutorService() {
      return Executors.newCachedThreadPool(new NamedThreadFactory("kinesis-cb"));
    }
  }

  public static class Module extends UpstartModule {
    @Override
    protected void configure() {
      serviceManager().manage(KinesisAsyncClientService.class)
              .manage(KinesisThreadPoolService.class);
    }
  }
}
