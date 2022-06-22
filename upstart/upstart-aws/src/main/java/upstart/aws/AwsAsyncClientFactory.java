package upstart.aws;

import software.amazon.awssdk.core.retry.RetryPolicy;
import upstart.aws.s3.SdkAsyncHttpClientService;
import software.amazon.awssdk.awscore.client.builder.AwsClientBuilder;
import software.amazon.awssdk.core.client.builder.SdkAsyncClientBuilder;
import software.amazon.awssdk.core.client.config.SdkAdvancedAsyncClientOption;

import javax.inject.Inject;
import java.util.concurrent.Executor;

public class AwsAsyncClientFactory {
  private final AwsClientFactory clientFactory;
  private final SdkAsyncHttpClientService httpClientService;

  @Inject
  public AwsAsyncClientFactory(AwsClientFactory clientFactory, SdkAsyncHttpClientService httpClientService) {
    this.clientFactory = clientFactory;
    this.httpClientService = httpClientService;
  }

  public AwsModule.AwsConfig getDefaultConfig() {
    return clientFactory.getDefaultConfig();
  }

  public <BuilderT extends AwsClientBuilder<BuilderT, ClientT> & SdkAsyncClientBuilder<BuilderT, ClientT>, ClientT> BuilderT configureAsyncClientBuilder(BuilderT builder, Executor completionExecutor) {
    return configureAsyncClientBuilder(builder, clientFactory.getDefaultConfig(), completionExecutor);
  }

  public <BuilderT extends AwsClientBuilder<BuilderT, ClientT> & SdkAsyncClientBuilder<BuilderT, ClientT>, ClientT> BuilderT configureAsyncClientBuilder(
          BuilderT builder,
          AwsModule.AwsConfig config,
          Executor completionExecutor
  ) {
    return clientFactory.configureClientBuilder(builder, config)
            .httpClient(httpClientService)
            .overrideConfiguration(b -> b.retryPolicy(RetryPolicy.defaultRetryPolicy().copy(rp -> rp.numRetries(config.maxRetries()))))
            .asyncConfiguration(b -> b.advancedOption(SdkAdvancedAsyncClientOption.FUTURE_COMPLETION_EXECUTOR, completionExecutor));
  }
}
