package upstart.aws;

import software.amazon.awssdk.awscore.client.builder.AwsAsyncClientBuilder;
import software.amazon.awssdk.awscore.client.builder.AwsClientBuilder;
import software.amazon.awssdk.core.SdkClient;
import upstart.util.concurrent.services.IdleService;
import upstart.util.concurrent.services.ThreadPoolService;

import java.util.function.Supplier;

public abstract class BaseAwsAsyncClientService<
        C extends SdkClient,
        B extends AwsClientBuilder<B, C> & AwsAsyncClientBuilder<B, C>
        > extends IdleService {
  private final AwsAsyncClientFactory clientFactory;
  private final ThreadPoolService threadPool;
  private C client;

  protected BaseAwsAsyncClientService(
          AwsAsyncClientFactory clientFactory,
          ThreadPoolService threadPool
  ) {
    this.clientFactory = clientFactory;
    this.threadPool = threadPool;
  }

  protected abstract B asyncClientBuilder();

  @Override
  protected void startUp() throws Exception {
    client = clientFactory.configureAsyncClientBuilder(asyncClientBuilder(), threadPool).build();
  }

  public C client() {
    return client;
  }

  @Override
  protected void shutDown() throws Exception {
    if (client != null) client.close();
  }
}
