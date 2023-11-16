package upstart.aws.kinesis;

import org.immutables.value.Value;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.kinesis.KinesisAsyncClient;
import software.amazon.awssdk.services.kinesis.model.CreateStreamRequest;
import software.amazon.awssdk.services.kinesis.model.PutRecordResponse;
import software.amazon.awssdk.services.kinesis.model.ResourceInUseException;
import software.amazon.awssdk.services.kinesis.model.StreamMode;
import upstart.aws.SdkPojoSerializer;
import upstart.config.UpstartModule;
import upstart.config.annotations.DeserializedImmutable;
import upstart.guice.AnnotationKeyedPrivateModule;
import upstart.guice.PrivateBinding;
import upstart.provisioning.BaseProvisionedResource;
import upstart.provisioning.ProvisionedResource;
import upstart.util.annotations.Tuple;
import upstart.util.concurrent.Promise;
import upstart.util.strings.NamingStyle;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.lang.annotation.Annotation;
import java.util.OptionalInt;
import java.util.regex.Pattern;

import static com.google.common.base.Preconditions.checkArgument;

@Singleton
public class KinesisStreamService extends BaseProvisionedResource {
  public static final ResourceType PROVISIONED_RESOURCE_TYPE = new ResourceType("kinesis_stream");

  private final StreamConfig streamConfig;
  private final KinesisAsyncClient client;

  @Inject
  public KinesisStreamService(@PrivateBinding StreamConfig streamConfig, KinesisAsyncClient client) {
    this.streamConfig = streamConfig;
    this.client = client;
  }

  public KinesisAsyncClient client() {
    return client;
  }

  public Promise<PutRecordResponse> send(String partitionKey, byte[] bytes) {
    return send(partitionKey, SdkBytes.fromByteArrayUnsafe(bytes));
  }

  public Promise<PutRecordResponse> send(String partitionKey, SdkBytes bytes) {
    return Promise.of(client().putRecord(b -> b.streamName(streamName()).partitionKey(partitionKey).data(bytes)));
  }

  public String streamName() {
    return streamConfig.name();
  }

  @Override
  public String resourceId() {
    return streamName();
  }

  @Override
  public ResourceType resourceType() {
    return PROVISIONED_RESOURCE_TYPE;
  }

  @Override
  public String ownerEnvironment() {
    return streamConfig.ownerEnvironment();
  }

  @Override
  public Object resourceConfig() {
    return SdkPojoSerializer.serialize(createStreamRequest(CreateStreamRequest.builder()).build(),
            NamingStyle.LowerCamelCaseSplittingAcronyms
    );
  }

  @Override
  public Promise<Void> provisionIfNotExists() {
    return Promise.of(client().createStream(this::createStreamRequest))
            .recover(ResourceInUseException.class, e -> null)
             // could still fail due to LimitExceededException, or other conditions? probably ok, supervisor will respawn
            .thenReplaceFuture(this::waitUntilProvisioned);
  }

  @Override
  public Promise<Void> waitUntilProvisioned() {
    return Promise.of(client().waiter().waitUntilStreamExists(b -> b.streamName(streamName()))).toVoid();
  }

  protected CreateStreamRequest.Builder createStreamRequest(CreateStreamRequest.Builder b) {
    return streamConfig.apply(b);
  }

  public static class Module extends UpstartModule {
    private final Annotation annotation;
    private final StreamConfig streamConfig;

    public Module(Annotation annotation, StreamConfig streamConfig) {
      super(annotation, streamConfig);
      this.annotation = annotation;
      this.streamConfig = streamConfig;
    }

    @Override
    protected void configure() {
      AnnotationKeyedPrivateModule privateModule = new AnnotationKeyedPrivateModule(
              annotation,
              KinesisStreamService.class
      ) {
        @Override
        protected void configurePrivateScope() {
          bindPrivateBinding(StreamConfig.class).toInstance(streamConfig);
        }
      };
      install(privateModule);
      ProvisionedResource.bindProvisionedResource(binder(), privateModule.annotatedKey(KinesisStreamService.class));
    }

  }

  @DeserializedImmutable
  public interface StreamConfig {
    Pattern VALID_NAME_PATTERN = Pattern.compile("[a-zA-Z0-9_.-]{1,128}");

    static ImmutableStreamConfig.Builder builder() {
      return ImmutableStreamConfig.builder();
    }

    String name();
    String ownerEnvironment();
    OptionalInt shardCount();
//    Optional<Duration> retentionPeriod(); // TODO support specifying retention , which might require a two-step init?

    default CreateStreamRequest.Builder apply(CreateStreamRequest.Builder b) {
      b.streamName(name());
      shardCount().ifPresentOrElse(
              shards -> b.shardCount(shards).streamModeDetails(d -> d.streamMode(StreamMode.PROVISIONED)),
              () -> b.streamModeDetails(d -> d.streamMode(StreamMode.ON_DEMAND))
      );
      return b;
    }


    @Value.Check
    default void check() {
      checkArgument(
              VALID_NAME_PATTERN.matcher(name()).matches(),
              "Stream name must match pattern: %s",
              VALID_NAME_PATTERN.pattern()
      );
      checkArgument(shardCount().isEmpty() || shardCount().getAsInt() >= 1, "Shard count must be >= 1");
//      checkArgument(retentionPeriod().filter(retention -> retention.toHours() < 1 || retention.toHours() > 8760).isEmpty(), "Retention period must be in [1, 8760] hours");
    }
  }
}
