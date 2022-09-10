package upstart.aws;

import upstart.config.UpstartModule;

public class AwsAsyncModule extends UpstartModule {
  @Override
  protected void configure() {
    bindConfig(SdkAsyncHttpClientService.AsyncClientConfig.class);
    serviceManager()
            .manage(SdkAsyncHttpClientService.class)
            .manage(AwsCallbackThreadPool.class);
            ;
  }
}
