package upstart.dynamodb;

public class SimpleDynamoTableReader<T> extends DynamoTableDao<T, T> {
  public SimpleDynamoTableReader(Class<T> storageClass, DynamoTable table) {
    super(storageClass, table, ItemExtractor.identity());
  }
}
