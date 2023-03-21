package upstart.dynamodb;

import static com.google.common.base.Preconditions.checkState;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.MoreCollectors;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import javax.inject.Inject;
import reactor.core.publisher.Flux;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbAsyncTable;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedAsyncClient;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.model.CreateTableEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.PagePublisher;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.model.*;
import upstart.healthchecks.HealthCheck;
import upstart.util.concurrent.CompletableFutures;
import upstart.util.concurrent.Promise;
import upstart.util.concurrent.services.AsyncService;
import upstart.util.reflect.Reflect;
import upstart.util.strings.NamingStyle;

public class DynamoTableInitializer<T> extends AsyncService implements HealthCheck {
  private static final LoadingCache<Class<?>, TableSchema<?>> TABLE_SCHEMAS =
      CacheBuilder.newBuilder().build(CacheLoader.from(TableSchema::fromBean));

  private final DynamoDbClientService dbService;
  protected final String tableName;
  protected final TableSchema<T> tableSchema;
  protected volatile DynamoDbAsyncTable<T> table;
  protected final Class<T> mappedClass;

  @Inject
  public DynamoTableInitializer(
      String tableNameSuffix,
      Class<T> mappedClass,
      DynamoDbClientService dbService,
      DynamoDbNamespace namespace) {
    tableName = namespace.tableName(tableNameSuffix);
    tableSchema = getTableSchema(mappedClass);
    this.dbService = dbService;
    this.mappedClass = mappedClass;
  }

  @SuppressWarnings("unchecked")
  public static <T> TableSchema<T> getTableSchema(Class<T> mappedClass) {
    return (TableSchema<T>) TABLE_SCHEMAS.getUnchecked(mappedClass);
  }

  public CompletableFuture<TableStatus> getTableStatus() {
    return describeTable().thenApply(resp -> resp.table().tableStatus());
  }

  public CompletableFuture<DescribeTableResponse> describeTable() {
    return dbService.describeTable(tableName);
  }

  public DynamoDbAsyncTable<T> table() {
    return table;
  }

  @Override
  protected CompletableFuture<?> startUp() throws Exception {
    var createTableRequest =
        prepareCreateTableRequest(CreateTableEnhancedRequest.builder()).build();
    return dbService
        .ensureTableCreated(tableName, tableSchema, createTableRequest)
        .thenCompose(this::tableCreated);
  }

  protected Promise<DynamoDbAsyncTable<T>> tableCreated(DynamoDbAsyncTable<T> table) {
    final Supplier<DynamoDbAsyncTable<T>> setTable = () -> this.table = table;
    return getTtlAttribute()
        .map(
            n ->
                Promise.of(dbService.updateTimeToLive(tableName, n)).thenApply(r -> setTable.get()))
        .orElseGet(() -> Promise.completed(setTable.get()));
  }

  private Optional<String> getTtlAttribute() {
    return Reflect.allAnnotatedMethods(
            mappedClass, TimeToLiveAttribute.class, Reflect.LineageOrder.SubclassBeforeSuperclass)
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

  /** override to add indices or provision throughput */
  protected CreateTableEnhancedRequest.Builder prepareCreateTableRequest(
      CreateTableEnhancedRequest.Builder builder) {
    return builder;
  }

  @Override
  protected CompletableFuture<?> shutDown() throws Exception {
    return CompletableFutures.nullFuture();
  }

  public static <B> Flux<B> itemFlux(PagePublisher<B> pagePublisher) {
    return Flux.from(pagePublisher.items());
  }

  public String tableName() {
    return tableName;
  }

  @Override
  public CompletableFuture<HealthStatus> checkHealth() {
    return getTableStatus()
        .thenApply(
            tableStatus ->
                HealthStatus.healthyIf(
                    tableStatus == TableStatus.ACTIVE,
                    "DynamoDB table '%s' was not ACTIVE: %s",
                    tableName(),
                    tableStatus));
  }

  protected DynamoDbEnhancedAsyncClient enhancedClient() {
    return dbService.enhancedClient();
  }

  public DynamoDbAsyncClient client() {
    return dbService.client();
  }

  public CompletableFuture<DescribeTableResponse> describeTable(String tableName) {
    return dbService.describeTable(tableName);
  }
}
