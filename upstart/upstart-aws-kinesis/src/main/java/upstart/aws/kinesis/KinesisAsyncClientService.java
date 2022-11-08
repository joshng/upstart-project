package upstart.aws.kinesis;

import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.kinesis.KinesisAsyncClient;
import software.amazon.awssdk.services.kinesis.model.PutRecordResponse;
import software.amazon.awssdk.services.kinesis.model.ResourceInUseException;
import upstart.util.concurrent.Promise;

import javax.inject.Inject;

public class KinesisAsyncClientService {
  private final KinesisAsyncClient client;

  @Inject
  public KinesisAsyncClientService(KinesisAsyncClient client) {
    this.client = client;
  }

  public KinesisAsyncClient client() {
    return client;
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
      return send(partitionKey, SdkBytes.fromByteArrayUnsafe(bytes));
    }

    public Promise<PutRecordResponse> send(String partitionKey, SdkBytes bytes) {
      return Promise.of(client().putRecord(b -> b.streamName(streamName).partitionKey(partitionKey).data(bytes)));
    }
  }
}
