package upstart.dynamodb;

import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.services.dynamodb.model.BillingMode;

public interface DynamoDbTableConfig {

  interface ProvisionedThroughput {
    long readCapacityUnits();
    long writeCapacityUnits();
  }

  BillingMode billingMode();


}
