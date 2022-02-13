package upstart.aws;

import com.google.inject.Key;
import com.google.inject.PrivateModule;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import upstart.aws.s3.SdkAsyncHttpClientService;
import upstart.config.UpstartModule;

public class AwsAsyncModule extends UpstartModule {
  @Override
  protected void configure() {
    install(AwsModule.class);
    bindConfig(SdkAsyncHttpClientService.AsyncClientConfig.class);
    serviceManager().manage(SdkAsyncHttpClientService.class);

    for (Aws.Service service : Aws.Service.values()) {
      Key<AwsClientFactory> factoryKey = Key.get(AwsClientFactory.class, service.annotation);
      Key<AwsAsyncClientFactory> asyncFactoryKey = Key.get(AwsAsyncClientFactory.class, service.annotation);

      install(new PrivateModule() {
        @Override
        protected void configure() {
          bind(AwsClientFactory.class).to(factoryKey);
          bind(asyncFactoryKey).to(AwsAsyncClientFactory.class);
          expose(asyncFactoryKey);
        }
      });
    }

  }
}
