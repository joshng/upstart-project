package upstart.aws.s3.test;

import akka.actor.ActorSystem;
import com.amazonaws.services.s3.model.ObjectMetadata;
import org.junit.jupiter.api.extension.ExtensionContext;
import software.amazon.awssdk.services.s3.S3Client;
import upstart.aws.AwsClientType;
import upstart.aws.s3.S3Key;
import upstart.config.EnvironmentConfigFixture;
import upstart.config.TestConfigBuilder;
import upstart.util.collect.MoreStreams;
import upstart.util.collect.Optionals;
import upstart.util.concurrent.Deadline;
import upstart.util.concurrent.services.IdleService;
import upstart.util.exceptions.UncheckedInterruptedException;
import io.findify.s3mock.S3Mock;
import io.findify.s3mock.error.NoSuchKeyException;
import io.findify.s3mock.provider.FileProvider;
import io.findify.s3mock.provider.GetObjectData;
import io.findify.s3mock.provider.InMemoryProvider;
import io.findify.s3mock.provider.Provider;
import io.findify.s3mock.provider.metadata.MetadataStore;
import io.findify.s3mock.request.CompleteMultipartUpload;
import io.findify.s3mock.request.CreateBucketConfiguration;
import io.findify.s3mock.response.CompleteMultipartUploadResult;
import io.findify.s3mock.response.CopyObjectResult;
import io.findify.s3mock.response.CreateBucket;
import io.findify.s3mock.response.InitiateMultipartUploadResult;
import io.findify.s3mock.response.ListAllMyBuckets;
import io.findify.s3mock.response.ListBucket;
import org.apache.http.entity.ContentType;
import org.hamcrest.Matcher;
import org.immutables.value.Value;
import scala.Option;
import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider;
import software.amazon.awssdk.awscore.client.builder.AwsClientBuilder;
import software.amazon.awssdk.regions.Region;

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static org.awaitility.Awaitility.await;

public class MockS3 extends IdleService implements EnvironmentConfigFixture {
  public static final String REGION = "us-west-2";
  private final Provider realProvider;
  private final S3Mock s3Mock;
  private final int port;
  private final Optional<Path> fileDirectory;
  private final URI endpointUri;
  private final List<S3Operation> operations = new ArrayList<>();


  MockS3(int port, Set<String> initialBuckets, Optional<Path> fileDirectory) {
    this.port = port;
    this.fileDirectory = fileDirectory;
    realProvider = fileDirectory.<Provider>map(realDir -> new FileProvider(realDir.toString())).orElseGet(InMemoryProvider::new);

    s3Mock = new S3Mock(port, new RecordingProvider(), ActorSystem.create("s3mock"));
    s3Mock.start();
    for (String bucket : initialBuckets) {
      createBucket(bucket);
    }
    endpointUri = URI.create("http://localhost:" + port);
  }

  @Override
  protected void startUp() throws Exception {
  }

  @Override
  protected void shutDown() throws Exception {
    s3Mock.stop();
  }

  private void onObjectCreated(String bucket, String key, byte[] data, ObjectMetadata metadata) {
    operations.add(ObjectCreation.builder().bucket(bucket).key(key).data(data).metadata(metadata).build());
    operations.notifyAll();
  }

  public int getPort() {
    return port;
  }

  public String getRegion() {
    return REGION;
  }

  public URI getEndpointUri() {
    return endpointUri;
  }

  public Optional<Path> getLocalFileDirectory() {
    return fileDirectory;
  }

  public MetadataStore metadataStore() {
    return realProvider.metadataStore();
  }

  public ListAllMyBuckets listBuckets() {
    return realProvider.listBuckets();
  }

  public ListBucket listBucket(String bucket, Option<String> prefix, Option<String> delimiter, Option<Object> maxkeys) {
    return realProvider.listBucket(bucket, prefix, delimiter, maxkeys);
  }

  public void createBucket(String name) {
    realProvider.createBucket(name, null);
  }

  public void putObject(String bucket, String key, byte[] data, ObjectMetadata metadata) {
    realProvider.putObject(bucket, key, data, metadata);
  }

  public void putObject(String bucket, String key, byte[] data, ContentType contentType) {
    ObjectMetadata metadata = new ObjectMetadata();
    metadata.setContentType(contentType.toString());
    putObject(bucket, key, data, metadata);
  }

  public void putObject(S3Key key, byte[] data, ContentType contentType) {
    putObject(key.bucket().value(), key.key(), data, contentType);
  }

  public void putFixture(S3Fixture fixture) {
    putObject(fixture.key(), fixture.data(), fixture.contentType());
  }

  public GetObjectData getObject(String bucket, String key) {
    return realProvider.getObject(bucket, key);
  }

  public GetObjectData getObject(S3Key key) {
    return getObject(key.bucket().value(), key.key());
  }

  public InitiateMultipartUploadResult putObjectMultipartStart(String bucket, String key, ObjectMetadata metadata) {
    return realProvider.putObjectMultipartStart(bucket, key, metadata);
  }

  public void putObjectMultipartPart(String bucket, String key, int partNumber, String uploadId, byte[] data) {
    realProvider.putObjectMultipartPart(bucket, key, partNumber, uploadId, data);
  }

  public CompleteMultipartUploadResult putObjectMultipartComplete(String bucket, String key, String uploadId, CompleteMultipartUpload request) {
    return realProvider.putObjectMultipartComplete(bucket, key, uploadId, request);
  }

  public void deleteObject(String bucket, String key) {
    realProvider.deleteObject(bucket, key);
  }

  public void deleteBucket(String bucket) {
    realProvider.deleteBucket(bucket);
  }

  public CopyObjectResult copyObject(String sourceBucket, String sourceKey, String destBucket, String destKey, Option<ObjectMetadata> newMeta) {
    return realProvider.copyObject(sourceBucket, sourceKey, destBucket, destKey, newMeta);
  }

  public CopyObjectResult copyObjectMultipart(String sourceBucket, String sourceKey, String destBucket, String destKey, int partNumber, String uploadId, int fromByte, int toByte, Option<ObjectMetadata> meta) {
    return realProvider.copyObjectMultipart(sourceBucket, sourceKey, destBucket, destKey, partNumber, uploadId, fromByte, toByte, meta);
  }

  public Option<String> normalizeDelimiter(Option<String> delimiter) {
    return realProvider.normalizeDelimiter(delimiter);
  }

  public byte[] assertObjectCreation(Duration timeout, String bucket, String key) {
    return await().ignoreException(NoSuchKeyException.class).atMost(timeout).until(() -> realProvider.getObject(bucket, key), x -> true).bytes();
  }

  public GetObjectData assertObjectCreation(Duration timeout, String bucket, String key, Matcher<GetObjectData> dataMatcher) {
    return await().ignoreException(NoSuchKeyException.class).atMost(timeout).until(() -> realProvider.getObject(bucket, key), dataMatcher);
  }

  public <BuilderT extends AwsClientBuilder<BuilderT, ClientT>, ClientT> BuilderT configureClientBuilder(BuilderT builder) {
    return builder.endpointOverride(endpointUri).region(Region.US_WEST_2).credentialsProvider(AnonymousCredentialsProvider.create());
  }

  public OperationLog newOperationLog() {
    return new OperationLog();
  }

  @Override
  public void applyEnvironmentValues(TestConfigBuilder<?> config, Optional<ExtensionContext> testExtensionContext) {
    config.overrideConfig(AwsClientType.of(S3Client.class).defaultConfigPath(), Map.of(
                                  "endpoint", endpointUri,
                                  "region", getRegion(),
                                  "credentialsProviderType", "Anonymous"
                          )
    );
  }

  public class OperationLog {
    private int cursor = 0;


    public Optional<S3Operation> awaitNextOperation(Deadline deadline) {
      return awaitNextMatch(deadline, op -> true);
    }

    public ObjectCreation expectObjectCreation(Deadline deadline, Predicate<? super ObjectCreation> selector) {
      return awaitNextMatch(ObjectCreation.class, deadline, selector).orElseThrow(() -> new AssertionError("No match within " + deadline.initialDuration().toMillis() + "ms"));
    }

    public <T extends S3Operation> Optional<T> awaitNextMatch(Class<T> type, Deadline deadline, Predicate<? super T> filter) {
      return awaitNextMatch(deadline, op -> Optionals.asInstance(op, type).filter(filter).isPresent()).map(type::cast);
    }

    public Optional<S3Operation> awaitNextMatch(Deadline deadline, Predicate<? super S3Operation> filter) {
      synchronized (operations) {
        while (true) {
          int newSize = operations.size();
          while (cursor < newSize) {
            S3Operation operation = operations.get(cursor++);
            if (filter.test(operation)) return Optional.of(operation);
          }
          Duration sleeptime = deadline.remaining();
          if (sleeptime.isNegative()) return Optional.empty();
          try {
            operations.wait(sleeptime.toMillis());
          } catch (InterruptedException e) {
            throw UncheckedInterruptedException.propagate(e);
          }
        }
      }
    }

    public Stream<S3Operation> streamUntil(Deadline deadline) {
      return MoreStreams.generate(() -> awaitNextOperation(deadline).orElse(null));
    }
  }

  public interface S3Operation {
    String bucket();
  }

  @Value.Immutable
  public interface ObjectCreation extends S3Operation {
    static ImmutableObjectCreation.Builder builder() {
      return ImmutableObjectCreation.builder();
    }
    String key();
    byte[] data();
    ObjectMetadata metadata();
  }

  private class RecordingProvider implements Provider {
    @Override
    public MetadataStore metadataStore() {
      return realProvider.metadataStore();
    }

    @Override
    public ListAllMyBuckets listBuckets() {
      return realProvider.listBuckets();
    }

    @Override
    public ListBucket listBucket(String bucket, Option<String> prefix, Option<String> delimiter, Option<Object> maxkeys) {
      return realProvider.listBucket(bucket, prefix, delimiter, maxkeys);
    }

    @Override
    public CreateBucket createBucket(String name, CreateBucketConfiguration bucketConfig) {
      return realProvider.createBucket(name, bucketConfig);
    }

    @Override
    public void putObject(String bucket, String key, byte[] data, ObjectMetadata metadata) {
      synchronized (operations){
        realProvider.putObject(bucket, key, data, metadata);
        onObjectCreated(bucket, key, data, metadata);
      }
    }

    @Override
    public GetObjectData getObject(String bucket, String key) {
      return realProvider.getObject(bucket, key);
    }

    @Override
    public InitiateMultipartUploadResult putObjectMultipartStart(String bucket, String key, ObjectMetadata metadata) {
      return realProvider.putObjectMultipartStart(bucket, key, metadata);
    }

    @Override
    public void putObjectMultipartPart(String bucket, String key, int partNumber, String uploadId, byte[] data) {
      realProvider.putObjectMultipartPart(bucket, key, partNumber, uploadId, data);
    }

    @Override
    public CompleteMultipartUploadResult putObjectMultipartComplete(String bucket, String key, String uploadId, CompleteMultipartUpload request) {
      synchronized (operations) {
        CompleteMultipartUploadResult result = realProvider.putObjectMultipartComplete(bucket, key, uploadId, request);
        GetObjectData data = realProvider.getObject(bucket, key);
        onObjectCreated(bucket, key, data.bytes(), data.metadata().get());
        return result;
      }
    }

    @Override
    public void deleteObject(String bucket, String key) {
      realProvider.deleteObject(bucket, key);
    }

    @Override
    public void deleteBucket(String bucket) {
      realProvider.deleteBucket(bucket);
    }

    @Override
    public CopyObjectResult copyObject(String sourceBucket, String sourceKey, String destBucket, String destKey, Option<ObjectMetadata> newMeta) {
      return realProvider.copyObject(sourceBucket, sourceKey, destBucket, destKey, newMeta);
    }

    @Override
    public CopyObjectResult copyObjectMultipart(String sourceBucket, String sourceKey, String destBucket, String destKey, int partNumber, String uploadId, int fromByte, int toByte, Option<ObjectMetadata> meta) {
      return realProvider.copyObjectMultipart(sourceBucket, sourceKey, destBucket, destKey, partNumber, uploadId, fromByte, toByte, meta);
    }

    @Override
    public Option<String> normalizeDelimiter(Option<String> delimiter) {
      return realProvider.normalizeDelimiter(delimiter);
    }
  }
}
