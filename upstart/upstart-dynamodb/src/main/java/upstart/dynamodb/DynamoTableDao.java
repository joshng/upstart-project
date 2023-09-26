package upstart.dynamodb;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbAsyncTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.CreateTableEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;
import upstart.util.concurrent.Promise;

import java.util.function.Consumer;

public class DynamoTableDao<B, T> extends BaseTableReader<B, T> implements DynamoTableReader<B, T> {
  private static final LoadingCache<Class<?>, TableSchema<?>> TABLE_SCHEMAS = CacheBuilder.newBuilder()
          .build(CacheLoader.from(TableSchema::fromBean));

  public static final Consumer<QueryEnhancedRequest.Builder> NOOP_QUERY_DECORATOR = new Consumer<>() {
    @Override
    public void accept(QueryEnhancedRequest.Builder builder) {
    }

    @SuppressWarnings("unchecked")
    @Override
    public Consumer<QueryEnhancedRequest.Builder> andThen(Consumer<? super QueryEnhancedRequest.Builder> after) {
      return (Consumer<QueryEnhancedRequest.Builder>) after;
    }
  };

  private final TableSchema<B> tableSchema;
  private final Class<B> beanClass;
  private final Consumer<QueryEnhancedRequest.Builder> queryDecorator;
  private final ItemExtractor<B, T> extractor;
  protected final DynamoTable table;
  protected final DynamoDbAsyncTable<B> enhancedTable;

  public DynamoTableDao(Class<B> beanClass, DynamoTable table, ItemExtractor<B, T> extractor) {
    this(table, beanClass, NOOP_QUERY_DECORATOR, extractor);
  }

  public DynamoTableDao(
          DynamoTable table,
          Class<B> beanClass,
          Consumer<QueryEnhancedRequest.Builder> queryDecorator,
          ItemExtractor<B, T> extractor
  ) {
    super(queryDecorator, extractor);
    this.table = table;
    this.beanClass = beanClass;
    this.tableSchema = getTableSchema(beanClass);
    this.queryDecorator = queryDecorator;
    this.extractor = extractor;
    this.enhancedTable = table.enhancedTable(tableSchema);
    table.registerApi(this);
  }

  @Override
  public Class<B> beanClass() {
    return beanClass;
  }

  public DynamoTable table() {
    return table;
  }

  /**
   * override to add indices or provision throughput
   */
  @Override
  public CreateTableEnhancedRequest.Builder prepareCreateTableRequest(CreateTableEnhancedRequest.Builder builder) {
    return builder;
  }

  @Override
  public Consumer<QueryEnhancedRequest.Builder> queryDecorator() {
    return queryDecorator;
  }

  @SuppressWarnings("unchecked")
  @Override
  public ItemExtractor<B, T> extractor() {
    return extractor;
  }

  @Override
  public DynamoTableDao<B, T> dao() {
    return this;
  }

  public DynamoDbAsyncTable<B> enhancedTable() {
    return enhancedTable;
  }


  @SuppressWarnings("unchecked")
  public static <S> TableSchema<S> getTableSchema(Class<S> mappedClass) {
    return (TableSchema<S>) TABLE_SCHEMAS.getUnchecked(mappedClass);
  }

  @Override
  public TableSchema<B> tableSchema() {
    return tableSchema;
  }
}
