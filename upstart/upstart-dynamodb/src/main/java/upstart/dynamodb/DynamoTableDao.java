package upstart.dynamodb;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.MoreCollectors;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbAsyncTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.model.CreateTableEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;
import upstart.util.concurrent.Promise;
import upstart.util.reflect.Reflect;
import upstart.util.strings.NamingStyle;

import java.util.Optional;
import java.util.function.Consumer;

import static com.google.common.base.Preconditions.checkState;

public class DynamoTableDao<S, T> implements DynamoTableReader<S, T> {
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

  private final TableSchema<S> tableSchema;
  private final Class<S> storageClass;
  private final Consumer<QueryEnhancedRequest.Builder> queryDecorator;
  private final ItemExtractor<S, T> extractor;
  protected volatile DynamoTable table;
  protected volatile DynamoDbAsyncTable<S> enhancedTable;

  public DynamoTableDao(Class<S> storageClass, DynamoTable table, ItemExtractor<S, T> extractor) {
    this(table, storageClass, NOOP_QUERY_DECORATOR, extractor);
  }

  public DynamoTableDao(
          DynamoTable table,
          Class<S> storageClass,
          Consumer<QueryEnhancedRequest.Builder> queryDecorator,
          ItemExtractor<S, T> extractor
  ) {
    this.storageClass = storageClass;
    this.tableSchema = getTableSchema(storageClass);
    this.queryDecorator = queryDecorator;
    this.extractor = extractor;
    table.registerReader(this);
  }

  public Class<S> mappedClass() {
    return storageClass;
  }

  protected Promise<?> onTableInitialized(DynamoTable table) {
    this.table = table;
    this.enhancedTable = table.enhancedTable(tableSchema);
    onEnhancedTableInitialized(enhancedTable);
    return Promise.nullPromise();
  }

  protected void onEnhancedTableInitialized(DynamoDbAsyncTable<S> enhancedTable) {
  }

  /**
   * override to add indices or provision throughput
   */
  protected CreateTableEnhancedRequest.Builder prepareCreateTableRequest(CreateTableEnhancedRequest.Builder builder) {
    return builder;
  }

  @Override
  public Consumer<QueryEnhancedRequest.Builder> queryDecorator() {
    return queryDecorator;
  }

  @SuppressWarnings("unchecked")
  @Override
  public ItemExtractor<S, T> extractor() {
    return extractor;
  }

  @Override
  public DynamoTableDao<S, T> dao() {
    return this;
  }

  public DynamoDbAsyncTable<S> enhancedTable() {
    return enhancedTable;
  }


  @SuppressWarnings("unchecked")
  public static <S> TableSchema<S> getTableSchema(Class<S> mappedClass) {
    return (TableSchema<S>) TABLE_SCHEMAS.getUnchecked(mappedClass);
  }

  public TableSchema<S> tableSchema() {
    return tableSchema;
  }

  Optional<String> getTtlAttribute() {
    return Reflect.allAnnotatedMethods(
                    storageClass, TimeToLiveAttribute.class, Reflect.LineageOrder.SubclassBeforeSuperclass)
            .collect(MoreCollectors.toOptional())
            .map(
                    meth ->
                            Optional.ofNullable(meth.getAnnotation(DynamoDbAttribute.class))
                                    .map(DynamoDbAttribute::value)
                                    .orElseGet(
                                            () -> {
                                              checkState(
                                                      meth.getName().startsWith("get"),
                                                      "Annotated bean method should start with 'get': %s",
                                                      meth.getName());
                                              return NamingStyle.UpperCamelCase.convertTo(
                                                      NamingStyle.LowerCamelCase, meth.getName().substring(3));
                                            }));
  }
}
