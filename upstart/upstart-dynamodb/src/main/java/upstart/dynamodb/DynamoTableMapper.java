package upstart.dynamodb;

import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.CreateTableEnhancedRequest;

import java.util.Optional;

public interface DynamoTableMapper {
  Class<?> beanClass();

  TableSchema<?> tableSchema();

  CreateTableEnhancedRequest.Builder prepareCreateTableRequest(CreateTableEnhancedRequest.Builder builder);

  Optional<String> getTtlAttribute();
}
