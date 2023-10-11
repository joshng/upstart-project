package upstart.dynamodb;

import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;

import java.util.function.Consumer;

public sealed abstract class BaseTableReader<B, T> implements DynamoTableReader<B, T> permits DynamoTableDao, BaseTableReader.Transformed {
  protected final Consumer<QueryEnhancedRequest.Builder> queryDecorator;
  protected final ItemExtractor<B, T> extractor;

  public BaseTableReader(Consumer<QueryEnhancedRequest.Builder> queryDecorator, ItemExtractor<B, T> extractor) {
    this.queryDecorator = queryDecorator;
    this.extractor = extractor;
  }

  public Consumer<QueryEnhancedRequest.Builder> queryDecorator() {
    return queryDecorator;
  }

  @Override
  public ItemExtractor<B, T> extractor() {
    return extractor;
  }

}
