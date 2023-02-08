package upstart.aws.kinesis;

import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.kinesis.KinesisAsyncClient;
import software.amazon.awssdk.services.kinesis.model.PutRecordResponse;
import software.amazon.awssdk.services.kinesis.model.ResourceInUseException;
import software.amazon.awssdk.services.kinesis.model.StreamMode;
import upstart.config.annotations.DeserializedImmutable;
import upstart.util.annotations.Tuple;
import upstart.util.concurrent.Promise;

import javax.inject.Inject;
import java.util.OptionalInt;

public class KinesisAsyncClientService {
  private final KinesisAsyncClient client;

  @Inject
  public KinesisAsyncClientService(KinesisAsyncClient client) {
    this.client = client;
  }

  public KinesisAsyncClient client() {
    return client;
  }

  public Promise<KinesisStreamPublisher> ensureStreamCreated(StreamConfig config) {
    return Promise.of(client().createStream(b -> {
              b.streamName(config.name());
              config.shardCount().ifPresentOrElse(shards -> b.shardCount(shards).streamModeDetails(d -> d.streamMode(StreamMode.PROVISIONED)),
                      () -> b.streamModeDetails(d -> d.streamMode(StreamMode.ON_DEMAND)));
            }))
            .recover(ResourceInUseException.class, e -> null)
             // could still fail due to LimitExceededException, or other conditions? probably ok, supervisor will respawn
            .thenReplaceFuture(() -> client().waiter().waitUntilStreamExists(b -> b.streamName(config.name())))
            .thenReplace(new KinesisStreamPublisher(config.name()));
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

  @DeserializedImmutable
  @Tuple
  public interface StreamConfig {
    static StreamConfig of(String streamName) {
      return ImmutableStreamConfig.of(streamName, OptionalInt.empty());
    }

    static StreamConfig of(String streamName, int shardCount) {
      return ImmutableStreamConfig.of(streamName, OptionalInt.of(shardCount));
    }

    String name();
    OptionalInt shardCount();
  }
}
