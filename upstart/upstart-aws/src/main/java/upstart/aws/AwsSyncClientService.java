package upstart.aws;

import software.amazon.awssdk.awscore.client.builder.AwsClientBuilder;
import software.amazon.awssdk.core.SdkClient;
import upstart.guice.PrivateBinding;

import javax.inject.Inject;

public class AwsSyncClientService<C extends SdkClient, B extends AwsClientBuilder<B, C>> extends AbstractAwsClientService<C, B> {
  private final AwsClientFactory clientFactory;

  @Inject
  public AwsSyncClientService(
          AwsClientFactory clientFactory,
          @SuppressWarnings("rawtypes") @PrivateBinding AwsClientType.SyncClient serviceType
  ) {
    super(serviceType);
    this.clientFactory = clientFactory;
  }

  @Override
  protected void configureBuilder(B builder) {
    clientFactory.configureClientBuilder(builder);
  }
}
