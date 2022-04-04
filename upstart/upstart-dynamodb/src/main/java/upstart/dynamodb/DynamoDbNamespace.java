package upstart.dynamodb;

import javax.inject.Inject;

public class DynamoDbNamespace {
  private final DynamoDbConfig context;

  @Inject
  public DynamoDbNamespace(DynamoDbConfig context) {
    this.context = context;
  }

  public String tableName(String suffix) {
    return context.namespace() + '.' + suffix;
  }

}
