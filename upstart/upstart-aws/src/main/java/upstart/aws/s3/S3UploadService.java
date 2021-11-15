package upstart.aws.s3;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import upstart.aws.AwsClientFactory;
import upstart.services.IdleService;
import upstart.services.ServiceLifecycle;
import org.immutables.value.Value;
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.http.async.SdkAsyncHttpClient;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

@Singleton
@ServiceLifecycle(ServiceLifecycle.Phase.Infrastructure)
public class S3UploadService extends IdleService {
  private final UploadConfig config;
  private final SdkAsyncHttpClient httpClient;
  private final AwsClientFactory clientFactory;
  private S3AsyncClient client;

  @Inject
  public S3UploadService(UploadConfig config, SdkAsyncHttpClient httpClient, AwsClientFactory clientFactory) {
    this.config = config;
    this.httpClient = httpClient;
    this.clientFactory = clientFactory;
  }

  /** TODO: consider more configuration settings for this client (eg, see {@link software.amazon.awssdk.core.SdkSystemSetting},
   * https://github.com/aws/aws-sdk-java-v2/blob/master/docs/BestPractices.md )
   */
  @Override
  protected void startUp() {
    client = clientFactory.configureClientBuilder(
            S3AsyncClient.builder()
                    .httpClient(httpClient)
                    .overrideConfiguration(b -> b.retryPolicy(rpBuilder -> rpBuilder.numRetries(config.maxRetries())))
    ).build();
  }

  public CompletableFuture<PutObjectResponse> upload(Path localFile, S3Key uri) {
    return upload(AsyncRequestBody.fromFile(localFile), uri);
  }

  public CompletableFuture<PutObjectResponse> upload(AsyncRequestBody requestBody, S3Key uri) {
    PutObjectRequest req = PutObjectRequest.builder()
            .bucket(uri.bucket().value())
            .key(uri.key())
            .build();

    return client.putObject(req, requestBody);
  }

  @Override
  protected void shutDown() {
    client.close();
  }

  @Value.Immutable
  @JsonDeserialize(as = ImmutableUploadConfig.class)
  public interface UploadConfig {
    static ImmutableUploadConfig.Builder builder() {
      return ImmutableUploadConfig.builder();
    }

    int maxRetries();
  }
}
