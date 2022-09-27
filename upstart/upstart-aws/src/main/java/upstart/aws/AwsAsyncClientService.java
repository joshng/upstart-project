package upstart.aws;

import software.amazon.awssdk.awscore.client.builder.AwsAsyncClientBuilder;
import software.amazon.awssdk.awscore.client.builder.AwsClientBuilder;
import software.amazon.awssdk.core.SdkClient;
import software.amazon.awssdk.core.client.builder.SdkAsyncClientBuilder;
import software.amazon.awssdk.core.client.config.SdkAdvancedAsyncClientOption;
import upstart.guice.PrivateBinding;
import upstart.managedservices.ServiceLifecycle;

import javax.inject.Inject;
import java.util.concurrent.Executor;

@ServiceLifecycle(ServiceLifecycle.Phase.Infrastructure)
public class AwsAsyncClientService<C extends SdkClient, B extends AwsClientBuilder<B, C> & AwsAsyncClientBuilder<B, C>> extends AbstractAwsClientService<C, B> {
  private final AwsAsyncClientFactory clientFactory;
  private final AwsCallbackThreadPool threadPool;

  @Inject
  public AwsAsyncClientService(
          @SuppressWarnings("rawtypes") @PrivateBinding AwsClientType.AsyncClient serviceType,
          AwsAsyncClientFactory clientFactory,
          AwsCallbackThreadPool threadPool
  ) {
    super(serviceType);
    //noinspection unchecked
    this.clientFactory = clientFactory;
    this.threadPool = threadPool;
  }

  @Override
  protected void configureBuilder(B builder) {
    clientFactory.configureAsyncClientBuilder(builder, getCallbackExecutor(threadPool));
  }

  protected Executor getCallbackExecutor(AwsCallbackThreadPool defaultThreadPool) {
    return command -> defaultThreadPool.execute(() -> {
      Thread currentThread = Thread.currentThread();
      String baseName = currentThread.getName();
      currentThread.setName(serviceName + '-' + baseName);
      try {
        command.run();
      } finally {
        currentThread.setName(baseName);
      }
    });
  }

  public static class AwsAsyncClientFactory {
    private final AwsClientFactory clientFactory;
    private final SdkAsyncHttpClientService httpClientService;

    @Inject
    public AwsAsyncClientFactory(AwsClientFactory clientFactory, SdkAsyncHttpClientService httpClientService) {
      this.clientFactory = clientFactory;
      this.httpClientService = httpClientService;
    }

    public <BuilderT extends AwsClientBuilder<BuilderT, ClientT> & SdkAsyncClientBuilder<BuilderT, ClientT>, ClientT> BuilderT configureAsyncClientBuilder(BuilderT builder, Executor completionExecutor) {
      return configureAsyncClientBuilder(builder, clientFactory.getDefaultConfig(), completionExecutor);
    }

    public <BuilderT extends AwsClientBuilder<BuilderT, ClientT> & SdkAsyncClientBuilder<BuilderT, ClientT>, ClientT> BuilderT configureAsyncClientBuilder(
            BuilderT builder,
            AwsConfig config,
            Executor completionExecutor
    ) {
      return clientFactory.configureClientBuilder(builder, config)
              .httpClient(httpClientService)
              .asyncConfiguration(b -> b.advancedOption(SdkAdvancedAsyncClientOption.FUTURE_COMPLETION_EXECUTOR, completionExecutor));
    }
  }
}
