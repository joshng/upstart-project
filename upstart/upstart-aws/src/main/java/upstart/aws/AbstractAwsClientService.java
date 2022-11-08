package upstart.aws;

import software.amazon.awssdk.awscore.client.builder.AwsClientBuilder;
import software.amazon.awssdk.core.SdkClient;
import upstart.managedservices.ResourceProviderService;
import upstart.managedservices.ServiceLifecycle;
import upstart.util.concurrent.services.IdleService;


@ServiceLifecycle(ServiceLifecycle.Phase.Infrastructure)
public abstract class AbstractAwsClientService<C extends SdkClient, B extends AwsClientBuilder<B, C>>
        extends IdleService
        implements ResourceProviderService<C>
{
  protected final AwsClientType<C, B> clientType;
  protected final String serviceName;
  protected C client;

  public AbstractAwsClientService(@SuppressWarnings("rawtypes") AwsClientType clientType) {
    this.clientType = (AwsClientType<C, B>) clientType;
    this.serviceName = clientType.serviceName();
  }

  protected abstract void configureBuilder(B builder);

  @Override
  protected void startUp() throws Exception {
    client = clientType.builder()
            .applyMutation(this::configureBuilder)
            .build();
  }

  @Override
  public String serviceName() {
    return super.serviceName() + '(' + serviceName + ')';
  }

  public C client() {
    return client;
  }

  @Override
  public C getResource() {
    return client;
  }

  @Override
  protected void shutDown() throws Exception {
    if (client != null) client.close();
  }
}
