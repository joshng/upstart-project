package upstart.aws;

import io.netty.handler.ssl.SslProvider;
import software.amazon.awssdk.http.async.AsyncExecuteRequest;
import software.amazon.awssdk.http.async.SdkAsyncHttpClient;
import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient;
import upstart.config.annotations.ConfigPath;
import upstart.util.concurrent.NamedThreadFactory;
import upstart.util.concurrent.services.IdleService;
import upstart.managedservices.ServiceLifecycle;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;

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
  protected boolean shutDownOnSeparateThread() {
    return false;
  }

  @Override
  protected void shutDown() throws Exception {
    // this shutdown routine takes a full 2 seconds for some reason, and we shouldn't be concerned about waiting for it,
    // so just do it in a separate thread
    Executors.newSingleThreadExecutor(new NamedThreadFactory("NettyNioAsyncHttpClient[D]").daemonize())
            .submit(delegate::close);
  }

  @Override
  public String clientName() {
    return delegate.clientName();
  }

  @Override
  public CompletableFuture<Void> execute(AsyncExecuteRequest request) {
    return delegate.execute(request);
  }

  @Override
  public void close() {
    throw new UnsupportedOperationException("Don't close the SdkAsyncHttpClient directly");
  }

  // TODO: make this configurable on a per-service/per-client basis, like the AwsConfig
  @ConfigPath("upstart.aws.asyncDefaults")
  public interface AsyncClientConfig {
    Duration connectionAcquisitionTimeout();
    int maxConcurrency();
  }
}
