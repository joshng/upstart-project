package upstart.dynamodb;

import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.CreateTableEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;
import upstart.util.concurrent.Promise;

import java.util.function.Consumer;

public class TransformedTableReader<B, T> extends BaseTableReader<B, T> {
  private final DynamoTableDao<B, ?> dao;

  protected TransformedTableReader(DynamoTableDao<B, ?> dao, Consumer<QueryEnhancedRequest.Builder> queryDecorator, ItemExtractor<B, T> extractor) {
    super(queryDecorator, extractor);
    this.dao = dao;
  }

  @Override
  public Class<?> beanClass() {
    return dao().beanClass();
  }

  @Override
  public CreateTableEnhancedRequest.Builder prepareCreateTableRequest(CreateTableEnhancedRequest.Builder builder) {
    return dao().prepareCreateTableRequest(builder);
  }

  @Override
  public TableSchema<B> tableSchema() {
    return dao().tableSchema();
  }

  @Override
  public DynamoTableDao<B, ?> dao() {
    return dao;
  }
}
