package upstart.aws;

import upstart.aws.s3.SdkAsyncHttpClientService;
import upstart.config.UpstartModule;

public class AwsAsyncModule extends UpstartModule {
  @Override
  protected void configure() {
    install(AwsModule.class);
    bindConfig(SdkAsyncHttpClientService.AsyncClientConfig.class);
    serviceManager().manage(SdkAsyncHttpClientService.class);
  }
}
