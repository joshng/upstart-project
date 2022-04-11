package upstart.aws.s3;

import io.netty.handler.ssl.SslProvider;
import software.amazon.awssdk.http.async.AsyncExecuteRequest;
import software.amazon.awssdk.http.async.SdkAsyncHttpClient;
import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient;
import upstart.config.annotations.ConfigPath;
import upstart.util.concurrent.services.IdleService;
import upstart.managedservices.ServiceLifecycle;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

@Singleton
@ServiceLifecycle(ServiceLifecycle.Phase.Infrastructure)
public class SdkAsyncHttpClientService extends IdleService implements SdkAsyncHttpClient {
  private final AsyncClientConfig config;
  private SdkAsyncHttpClient delegate;

  @Inject
  public SdkAsyncHttpClientService(AsyncClientConfig config) {
    this.config = config;
  }

  @Override
  protected void startUp() {
    delegate = NettyNioAsyncHttpClient.builder()
            .sslProvider(SslProvider.OPENSSL)
            .connectionAcquisitionTimeout(config.connectionAcquisitionTimeout())
            .maxConcurrency(config.maxConcurrency())
            .build();
  }

  @Override
  protected void shutDown() throws Exception {
    delegate.close();
  }

  @Override
  public CompletableFuture<Void> execute(AsyncExecuteRequest request) {
    return delegate.execute(request);
  }

  @Override
  public void close() {
    throw new UnsupportedOperationException("Don't close the SdkAsyncHttpClient directly");
  }

  @ConfigPath("upstart.aws.asyncDefaults")
  public interface AsyncClientConfig {
    Duration connectionAcquisitionTimeout();
    int maxConcurrency();
  }
}
