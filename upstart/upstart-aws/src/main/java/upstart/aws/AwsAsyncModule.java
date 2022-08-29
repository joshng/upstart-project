package upstart.aws;

import upstart.aws.s3.SdkAsyncHttpClientService;
import upstart.config.UpstartModule;
import upstart.guice.AnnotationKeyedPrivateModule;

public class AwsAsyncModule extends UpstartModule {
  @Override
  protected void configure() {
    install(AwsModule.class);
    bindConfig(SdkAsyncHttpClientService.AsyncClientConfig.class);
    serviceManager().manage(SdkAsyncHttpClientService.class);

    for (Aws.Service service : Aws.Service.values()) {
      install(new AnnotationKeyedPrivateModule(service.annotation, AwsAsyncClientFactory.class) {
        @Override
        protected void configurePrivateScope() {
          bindPrivateBindingToAnnotatedKey(AwsClientFactory.class);
        }
      });
    }

  }
}
