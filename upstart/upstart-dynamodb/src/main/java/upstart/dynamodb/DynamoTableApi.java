package upstart.dynamodb;

import com.google.common.collect.MoreCollectors;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.model.CreateTableEnhancedRequest;
import upstart.util.concurrent.Promise;
import upstart.util.reflect.Reflect;
import upstart.util.strings.NamingStyle;

import java.util.Optional;

import static com.google.common.base.Preconditions.checkState;

public interface DynamoTableApi {
  Class<?> beanClass();

  CreateTableEnhancedRequest.Builder prepareCreateTableRequest(CreateTableEnhancedRequest.Builder builder);

  TableSchema<?> tableSchema();

  Optional<String> getTtlAttribute();
}
