package upstart.dynamodb;

import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import upstart.aws.AwsClientModule;
import upstart.config.UpstartModule;

public class DynamoDbModule extends UpstartModule {
  @Override
  protected void configure() {
    AwsClientModule.installWithDefaultConfig(binder(), DynamoDbAsyncClient.class);
  }
}
