package upstart.dynamodb;

import upstart.aws.AwsAsyncModule;
import upstart.config.UpstartModule;

public class DynamoDbModule extends UpstartModule {
  @Override
  protected void configure() {
    install(AwsAsyncModule.class);
    bindConfig(DynamoDbConfig.class);
    serviceManager()
            .manage(DynamoDbClientService.DynamoThreadPoolService.class)
            .manage(DynamoDbClientService.class);
  }
}
