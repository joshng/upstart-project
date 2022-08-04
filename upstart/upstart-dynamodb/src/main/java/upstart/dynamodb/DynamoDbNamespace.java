package upstart.dynamodb;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import javax.inject.Inject;

public class DynamoDbNamespace {
  private final String namespace;

  @Inject
  public DynamoDbNamespace(DynamoDbConfig context) {
    this(context.namespace());
  }

  @JsonCreator
  public DynamoDbNamespace(String namespace) {
    this.namespace = namespace;
  }

  public String tableName(String suffix) {
    return namespace + '.' + suffix;
  }

  @JsonValue
  public String namespace() {
    return namespace;
  }
}
