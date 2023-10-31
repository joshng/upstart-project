package upstart.aws.s3;

import com.google.inject.Binder;
import com.google.inject.Key;
import com.google.inject.name.Names;
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.core.async.AsyncResponseTransformer;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.GetBucketAnalyticsConfigurationRequest;
import software.amazon.awssdk.services.s3.model.GetBucketAnalyticsConfigurationResponse;
import software.amazon.awssdk.services.s3.model.GetBucketPolicyResponse;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectVersionsRequest;
import software.amazon.awssdk.services.s3.model.ListObjectVersionsResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.PutBucketPolicyRequest;
import software.amazon.awssdk.services.s3.model.PutBucketPolicyResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.paginators.ListObjectVersionsPublisher;
import software.amazon.awssdk.services.s3.paginators.ListObjectsV2Publisher;
import upstart.aws.AwsClientModule;
import upstart.aws.AwsConfig;
import upstart.aws.SdkPojoSerializer;
import upstart.config.UpstartModule;
import upstart.guice.AnnotationKeyedPrivateModule;
import upstart.guice.PrivateBinding;
import upstart.provisioning.BaseProvisionedResource;
import upstart.provisioning.ProvisionedResource;
import upstart.util.concurrent.Promise;
import upstart.util.strings.NamingStyle;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.lang.annotation.Annotation;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import static com.google.common.base.Preconditions.checkArgument;

@Singleton
public class S3BucketClient extends BaseProvisionedResource {
  public static final ResourceType PROVISIONED_RESOURCE_TYPE = new ResourceType("s3_bucket");
  private final S3Bucket bucket;
  private final CreateBucketRequestBuilder createBucketRequest;
  private final AwsConfig awsConfig;
  private final S3BucketConfig bucketConfig;
  private final S3AsyncClient s3Client;

  @Inject
  S3BucketClient(
          @PrivateBinding S3Bucket bucket,
          @PrivateBinding CreateBucketRequestBuilder createBucketRequest,
          @PrivateBinding AwsConfig awsConfig,
          @PrivateBinding S3BucketConfig bucketConfig,
          S3AsyncClient s3Client
  ) {
    this.bucket = bucket;
    this.awsConfig = awsConfig;
    this.bucketConfig = bucketConfig;
    this.s3Client = s3Client;
    this.createBucketRequest = createBucketRequest;
  }

  public Promise<GetBucketAnalyticsConfigurationResponse> getBucketAnalyticsConfiguration(Consumer<GetBucketAnalyticsConfigurationRequest.Builder> getBucketAnalyticsConfigurationRequest) {
    return Promise.of(s3Client.getBucketAnalyticsConfiguration(getBucketAnalyticsConfigurationRequest.andThen(b -> b.bucket(bucketName()))));
  }

  private String bucketName() {
    return bucket.value();
  }

  public <ReturnT> Promise<ReturnT> getObject(
          S3Key key,
          AsyncResponseTransformer<GetObjectResponse, ReturnT> asyncResponseTransformer
  ) {
    return getObject(keyInBucket(key), asyncResponseTransformer);
  }

  public <ReturnT> Promise<ReturnT> getObject(
          String key,
          AsyncResponseTransformer<GetObjectResponse, ReturnT> asyncResponseTransformer
  ) {
    return getObject(b -> b.key(key), asyncResponseTransformer);
  }

  public <ReturnT> Promise<ReturnT> getObject(
          Consumer<GetObjectRequest.Builder> getObjectRequest,
          AsyncResponseTransformer<GetObjectResponse, ReturnT> asyncResponseTransformer
  ) {
    return Promise.of(s3Client.getObject(getObjectRequest.andThen(b -> b.bucket(bucketName())), asyncResponseTransformer));
  }

  public Promise<GetObjectResponse> getObject(S3Key key, Path destinationPath) {
    return getObject(keyInBucket(key), destinationPath);
  }

  public Promise<GetObjectResponse> getObject(String key, Path destinationPath) {
    return getObject(b -> b.key(key), destinationPath);
  }

  public Promise<GetObjectResponse> getObject(
          Consumer<GetObjectRequest.Builder> getObjectRequest,
          Path destinationPath
  ) {
    return Promise.of(s3Client.getObject(getObjectRequest.andThen(b -> b.bucket(bucketName())), destinationPath));
  }

  public Promise<HeadBucketResponse> headBucket() {
    return headBucket(b -> {});
  }

  public Promise<HeadBucketResponse> headBucket(Consumer<HeadBucketRequest.Builder> headBucketRequest) {
    return Promise.of(s3Client.headBucket(headBucketRequest.andThen(b -> b.bucket(bucketName()))));
  }

  public Promise<HeadObjectResponse> headObject(String key) {
    return headObject(b -> b.key(key));
  }

  public Promise<HeadObjectResponse> headObject(Consumer<HeadObjectRequest.Builder> headObjectRequest) {
    return Promise.of(s3Client.headObject(headObjectRequest.andThen(b -> b.bucket(bucketName()))));
  }

  public Promise<ListObjectVersionsResponse> listObjectVersions(Consumer<ListObjectVersionsRequest.Builder> listObjectVersionsRequest) {
    return Promise.of(s3Client.listObjectVersions(listObjectVersionsRequest.andThen(b -> b.bucket(bucketName()))));
  }

  public ListObjectVersionsPublisher listObjectVersionsPaginator(Consumer<ListObjectVersionsRequest.Builder> listObjectVersionsRequest) {
    return s3Client.listObjectVersionsPaginator(listObjectVersionsRequest.andThen(b -> b.bucket(bucketName())));
  }

  public Promise<ListObjectsResponse> listObjects(String prefix) {
    return listObjects(b -> b.prefix(prefix));
  }

  public Promise<ListObjectsResponse> listObjects(Consumer<ListObjectsRequest.Builder> listObjectsRequest) {
    return Promise.of(s3Client.listObjects(listObjectsRequest.andThen(b -> b.bucket(bucketName()))));
  }

  public Promise<ListObjectsV2Response> listObjectsV2(String prefix) {
    return listObjectsV2(b -> b.prefix(prefix));
  }

  public Promise<ListObjectsV2Response> listObjectsV2(Consumer<ListObjectsV2Request.Builder> listObjectsV2Request) {
    return Promise.of(s3Client.listObjectsV2(listObjectsV2Request.andThen(b -> b.bucket(bucketName()))));
  }

  public ListObjectsV2Publisher listObjectsV2Paginator(Consumer<ListObjectsV2Request.Builder> listObjectsV2Request) {
    return s3Client.listObjectsV2Paginator(listObjectsV2Request.andThen(b -> b.bucket(bucketName())));
  }

  public Promise<PutObjectResponse> putObject(S3Key key, AsyncRequestBody requestBody) {
    return putObject(keyInBucket(key), requestBody);
  }

  public Promise<PutObjectResponse> putObject(S3Key key, Path sourcePath) {
    return putObject(key.putObjectRequestBuilder(), sourcePath);
  }

  public Promise<PutObjectResponse> putObject(String path, AsyncRequestBody requestBody) {
    return putObject(b -> b.key(path), requestBody);
  }

  public Promise<PutObjectResponse> putObject(
          Consumer<PutObjectRequest.Builder> putObjectRequest,
          AsyncRequestBody requestBody
  ) {
    return Promise.of(s3Client.putObject(putObjectRequest.andThen(b -> b.bucket(bucketName())), requestBody));
  }

  public Promise<PutBucketPolicyResponse> putBucketPolicy(String policy) {
    return putBucketPolicy(b -> b.policy(policy));
  }

  public Promise<PutBucketPolicyResponse> putBucketPolicy(Consumer<PutBucketPolicyRequest.Builder> putBucketPolicyRequest) {
    return Promise.of(s3Client.putBucketPolicy(putBucketPolicyRequest.andThen(b -> b.bucket(bucketName()))));
  }

  public CompletableFuture<GetBucketPolicyResponse> getBucketPolicy() {
    return s3Client.getBucketPolicy(b -> b.bucket(bucketName()));
  }

  public Promise<PutObjectResponse> putObject(
          Consumer<PutObjectRequest.Builder> putObjectRequest,
          Path sourcePath
  ) {
    return Promise.of(s3Client.putObject(putObjectRequest.andThen(b -> b.bucket(bucketName())), sourcePath));
  }

  @Override
  public String resourceId() {
    return bucketName();
  }

  @Override
  public ResourceType resourceType() {
    return PROVISIONED_RESOURCE_TYPE;
  }

  @Override
  public String ownerEnvironment() {
    return bucketConfig.ownerEnvironment();
  }

  @Override
  public Object resourceConfig() {
    return SdkPojoSerializer.serialize(
            buildCreateBucketRequest(CreateBucketRequest.builder()).build(),
            NamingStyle.LowerCamelCaseGroupingAcronyms
    );
  }

  @Override
  public Promise<Void> waitUntilProvisioned() {
    return Promise.of(s3Client.waiter().waitUntilBucketExists(b -> b.bucket(bucketName()))).toVoid();
  }

  @Override
  public Promise<Void> provisionIfNotExists() {
    return headBucket()
            .toVoid()
            .recoverCompose(NoSuchBucketException.class, e -> Promise.of(
                    s3Client.createBucket(this::buildCreateBucketRequest)).toVoid()
            );
  }

  private CreateBucketRequest.Builder buildCreateBucketRequest(CreateBucketRequest.Builder b) {
    return b.applyMutation(createBucketRequest).bucket(bucketName());
  }

  private String keyInBucket(S3Key key) {
    checkArgument(key.bucket().equals(bucket), "S3Key must be in the same bucket as this client");
    return key.key();
  }

  public static void bindBucketWithDefaultConfigs(Binder binder, S3Bucket bucket, S3BucketConfig bucketConfig, Annotation bindingAnnotation) {
    bindBucket(AwsClientModule.withDefaultConfig(binder, S3AsyncClient.class), bucket, bucketConfig, bindingAnnotation);
  }

  public static void bindBucket(
          AwsClientModule<S3AsyncClient> clientModule,
          S3Bucket bucket,
          S3BucketConfig bucketConfig,
          Annotation bindingAnnotation
  ) {
    bindBucket(clientModule.requiredBinder(), clientModule, bucket, bucketConfig, bindingAnnotation);
  }

  public static void bindBucket(
          Binder binder,
          AwsClientModule<S3AsyncClient> clientModule,
          S3Bucket bucket,
          S3BucketConfig bucketConfig,
          Annotation bindingAnnotation
  ) {
    binder.install(new Module(
            bucket,
            bindingAnnotation,
            DefaultCreateBucketRequestModule.CREATE_BUCKET_REQUEST_KEY,
            bucketConfig,
            clientModule
    ));
    binder.install(DefaultCreateBucketRequestModule.Instance);
  }

  public Region region() {
    return awsConfig.sdkRegion().orElseThrow();
  }

  public S3Bucket bucket() {
    return bucket;
  }

  public interface CreateBucketRequestBuilder extends Consumer<CreateBucketRequest.Builder> {
    CreateBucketRequestBuilder NONE = b -> {};
  }

  private enum DefaultCreateBucketRequestModule implements com.google.inject.Module {
    Instance;

    public static final Key<CreateBucketRequestBuilder> CREATE_BUCKET_REQUEST_KEY = Key.get(CreateBucketRequestBuilder.class, Names.named("default"));

    @Override
    public void configure(Binder binder) {
      binder.bind(CREATE_BUCKET_REQUEST_KEY).toInstance(CreateBucketRequestBuilder.NONE);
    }
  }

  public static class Module extends UpstartModule {
    private final S3Bucket bucket;
    private final Annotation bindingAnnotation;
    private final Key<? extends CreateBucketRequestBuilder> createBucketRequestKey;
    private final S3BucketConfig bucketConfig;
    private final AwsClientModule<S3AsyncClient> clientModule;


    public Module(
            S3Bucket bucket,
            Annotation bindingAnnotation,
            Key<? extends CreateBucketRequestBuilder> createBucketRequestKey,
            S3BucketConfig bucketConfig,
            AwsClientModule<S3AsyncClient> clientModule
    ) {
      super(bucket); // intentionally not considering other parameters for equality, to reject multiple bindings for the same bucket
      this.bucket = bucket;
      this.bindingAnnotation = bindingAnnotation;
      this.createBucketRequestKey = createBucketRequestKey;
      this.bucketConfig = bucketConfig;
      this.clientModule = clientModule;
    }


    @Override
    protected void configure() {
      install(clientModule);
      AnnotationKeyedPrivateModule privateModule = new AnnotationKeyedPrivateModule(
              bindingAnnotation,
              S3BucketClient.class
      )
      {
        @Override
        protected void configurePrivateScope() {
          bindPrivateBinding(S3Bucket.class).toInstance(bucket);
          bindPrivateBinding(CreateBucketRequestBuilder.class).to(createBucketRequestKey);
          bindPrivateBinding(AwsConfig.class).to(clientModule.awsConfigKey());
          bindPrivateBinding(S3BucketConfig.class).toInstance(bucketConfig);
        }
      };
      install(privateModule);

      ProvisionedResource.bindProvisionedResource(binder(), privateModule.annotatedKey(S3BucketClient.class));
    }
  }

  public interface S3BucketConfig {
    static ImmutableS3BucketConfig.Builder builder() {
      return ImmutableS3BucketConfig.builder();
    }

    String ownerEnvironment();
  }
}
