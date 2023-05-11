package upstart.aws.test.dynamodb;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;
import upstart.aws.AwsClientModule;
import upstart.config.UpstartModule;
import upstart.test.UpstartLibraryServiceTest;
import upstart.test.UpstartLibraryTest;
import upstart.test.UpstartServiceTest;

import javax.inject.Inject;
import java.util.Map;

import static com.google.common.truth.Truth.assertThat;

@LocalDynamoDbTest
@UpstartLibraryServiceTest
class LocalDynamoDBExtensionTest extends UpstartModule {
  @Inject DynamoDbClient injectedClient;
  @Override
  protected void configure() {
    AwsClientModule.installWithDefaultConfig(binder(), DynamoDbClient.class);
  }

  @Test
  void putDynamoObj(DynamoDbClient client) {
    String tableName = "josh";
    String key = "key-1";
    //noinspection unchecked
    client.createTable(b -> b
            .tableName(tableName)
            .keySchema(sb -> sb.attributeName("key").keyType(KeyType.HASH))
            .attributeDefinitions(
                    AttributeDefinition.builder().attributeName("key").attributeType(ScalarAttributeType.S).build()
            ).provisionedThroughput(tb -> tb.readCapacityUnits(1L).writeCapacityUnits(1L))
    );

    client.putItem(b -> b.tableName(tableName)
            .item(Map.of(
                          "key", AttributeValue.builder().s(key).build(),
                          "val", AttributeValue.builder().s("a-value").build()
                  )
            ));

    GetItemResponse response = injectedClient.getItem(b -> b
            .tableName(tableName)
            .key(Map.of("key", AttributeValue.builder().s(key).build()))
            .projectionExpression("val")
    );
    assertThat(response.item().get("val").s()).isEqualTo("a-value");
  }
}