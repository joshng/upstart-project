package upstart.dynamodb;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import reactor.core.publisher.Flux;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbAsyncTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.Page;
import software.amazon.awssdk.enhanced.dynamodb.model.PagePublisher;
import software.amazon.awssdk.services.dynamodb.model.DescribeTableResponse;
import software.amazon.awssdk.services.dynamodb.model.TableStatus;
import upstart.healthchecks.HealthCheck;
import upstart.util.concurrent.services.AsyncService;
import upstart.util.concurrent.CompletableFutures;

import javax.inject.Inject;
import java.util.concurrent.CompletableFuture;

public abstract class DynamoTableInitializer<T> extends AsyncService implements HealthCheck {
  private static final LoadingCache<Class<?>, TableSchema<?>> TABLE_SCHEMAS = CacheBuilder.newBuilder()
          .build(CacheLoader.from(TableSchema::fromBean));

  private final String tableNameSuffix;
  private final TableSchema<T> tableSchema;
  private final DynamoDbClientService db;
  private final DynamoDbNamespace namespace;
  private final String tableName;
  protected volatile DynamoDbAsyncTable<T> table;

  @Inject
  public DynamoTableInitializer(
          String tableNameSuffix,
          Class<T> mappedClass,
          DynamoDbClientService db,
          DynamoDbNamespace namespace
  ) {
    this.tableNameSuffix = tableNameSuffix;
    tableSchema = getTableSchema(mappedClass);
    this.db = db;
    this.namespace = namespace;
    tableName = namespace.tableName(tableNameSuffix);
  }

  @SuppressWarnings("unchecked")
  public static <T> TableSchema<T> getTableSchema(Class<T> mappedClass) {
    return (TableSchema<T>) TABLE_SCHEMAS.getUnchecked(mappedClass);
  }

  public CompletableFuture<TableStatus> getTableStatus() {
    return describeTable().thenApply(resp -> resp.table().tableStatus());
  }

  public CompletableFuture<DescribeTableResponse> describeTable() {
    return db.describeTable(tableName);
  }

  protected DynamoDbAsyncTable<T> table() {
    return table;
  }

  @Override
  protected CompletableFuture<?> startUp() throws Exception {
    return db.ensureTableCreated(tableName, tableSchema)
            .thenAccept(table -> this.table = table);
  }

  @Override
  protected CompletableFuture<?> shutDown() throws Exception {
    return CompletableFutures.nullFuture();
  }

  public static <B> Flux<B> consumeItems(PagePublisher<B> pagePublisher) {
    return Flux.from(pagePublisher)
            .map(Page::items)
            .flatMap(Flux::fromIterable);
  }

  public String tableName() {
    return tableName;
  }

  @Override
  public CompletableFuture<HealthStatus> checkHealth() {
    return getTableStatus()
            .thenApply(tableStatus -> HealthStatus.healthyIf(
                    tableStatus == TableStatus.ACTIVE,
                    "DynamoDB table '%s' was not ACTIVE: %s",
                    tableName(),
                    tableStatus
            ));
  }
}
