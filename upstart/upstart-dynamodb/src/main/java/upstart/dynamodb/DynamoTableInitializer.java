package upstart.dynamodb;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.MoreCollectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbAsyncTable;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedAsyncClient;
import software.amazon.awssdk.enhanced.dynamodb.OperationContext;
import software.amazon.awssdk.enhanced.dynamodb.TableMetadata;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.internal.operations.CreateTableOperation;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.model.CreateTableEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.PagePublisher;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.model.CreateTableRequest;
import software.amazon.awssdk.services.dynamodb.model.DescribeTableResponse;
import software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException;
import software.amazon.awssdk.services.dynamodb.model.TableStatus;
import software.amazon.awssdk.services.dynamodb.model.TimeToLiveStatus;
import upstart.aws.SdkPojoSerializer;
import upstart.healthchecks.HealthCheck;
import upstart.provisioning.BaseProvisionedResource;
import upstart.util.collect.PersistentMap;
import upstart.util.concurrent.Promise;
import upstart.util.concurrent.ShutdownException;
import upstart.util.reflect.Reflect;
import upstart.util.strings.NamingStyle;

import javax.inject.Inject;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static com.google.common.base.Preconditions.checkState;

public class DynamoTableInitializer<T> extends BaseProvisionedResource implements HealthCheck {
  private static final Logger LOG = LoggerFactory.getLogger(DynamoTableInitializer.class);
  private static final LoadingCache<Class<?>, TableSchema<?>> TABLE_SCHEMAS = CacheBuilder.newBuilder()
          .build(CacheLoader.from(TableSchema::fromBean));
  public static final ResourceType PROVISIONED_RESOURCE_TYPE = new ResourceType("dynamodb_table");

  private final DynamoDbClientService dbService;
  private final String tableName;
  private final TableSchema<T> tableSchema;
  private final Class<T> mappedClass;
  protected volatile DynamoDbAsyncTable<T> table;

  @Inject
  public DynamoTableInitializer(
          String tableNameSuffix,
          Class<T> mappedClass,
          DynamoDbClientService dbService,
          DynamoDbNamespace namespace
  ) {
    this.mappedClass = mappedClass;
    tableName = namespace.tableName(tableNameSuffix);
    tableSchema = getTableSchema(mappedClass);
    this.dbService = dbService;
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

  private Promise<Void> initTable(Promise<DynamoDbAsyncTable<T>> tablePromise) {
    return tablePromise.thenCompose(t -> {
      this.table = t;
      return onTableInitializedAsync(t);
    });
  }

  protected Promise<Void> onTableInitializedAsync(DynamoDbAsyncTable<T> table) {
    onTableInitialized(table);
    return Promise.nullPromise();
  }

  protected void onTableInitialized(DynamoDbAsyncTable<T> table) {
  }


  @Override
  public String resourceId() {
    return tableName;
  }

  @Override
  public ResourceType resourceType() {
    return PROVISIONED_RESOURCE_TYPE;
  }

  @Override
  public Object resourceConfig() {
    CreateTableRequest request = CreateTableOperation.<T>create(createTableRequest()).generateRequest(
            tableSchema,
            new OperationContext() {
              @Override
              public String tableName() {
                return tableName;
              }

              @Override
              public String indexName() {
                return TableMetadata.primaryIndexName();
              }
            },
            null
    );

    PersistentMap<String, Object> tableSpec = SdkPojoSerializer.serialize(
            request,
            NamingStyle.LowerCamelCaseSplittingAcronyms
    );

    // this AWS SDK doesn't support the inline TTL attribute specification (?!), so we have to add it manually
    return getTtlAttribute()
            .map(ttlAttr -> tableSpec.plus("timeToLiveSpecification", Map.of(
                    "attributeName", ttlAttr,
                    "enabled", true
                    )
            )).orElse(tableSpec);
  }

  @Override
  public Promise<Void> provisionIfNotExists() {
    Promise<Void> created = dbService.ensureTableCreated(tableName, tableSchema, createTableRequest());
    return getTtlAttribute()
            .map(ttlAttr -> created.thenReplaceFuture(() -> applyTtl(ttlAttr)))
            .orElse(created);
  }

  @Override
  public Promise<Void> waitUntilProvisioned() {
    return initTable(dbService.waitUntilTableExists(tableName, tableSchema, Duration.ofSeconds(5), promise -> {
      if (wasStartupCanceled()) {
        promise.completeExceptionally(new ShutdownException("Startup was canceled"));
      } else {
        LOG.warn("Waiting for table '{}' to be ready...", tableName);
      }
    }));
  }

  private CreateTableEnhancedRequest createTableRequest() {
    return prepareCreateTableRequest(
            CreateTableEnhancedRequest.builder()
    ).build();
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

  private Promise<Void> applyTtl(String ttlAttr) {
    return waitUntilProvisioned()
            .thenReplaceFuture(() -> dbService.client().describeTimeToLive(b -> b.tableName(tableName)))
            .thenFilterOptional(
                    r -> r.timeToLiveDescription().timeToLiveStatus() != TimeToLiveStatus.ENABLED)
            .thenMapCompose(
                    __ ->
                            Promise.of(dbService.updateTimeToLive(tableName, ttlAttr))
                                    .thenFilterOptional(r -> r.timeToLiveSpecification().enabled())
                                    .orElseThrow(() -> new RuntimeException("Failed to enable TTL"))
            ).toVoid();
  }

  /**
   * override to add indices or provision throughput
   */
  protected CreateTableEnhancedRequest.Builder prepareCreateTableRequest(CreateTableEnhancedRequest.Builder builder) {
    return builder;
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
            .thenApply(tableStatus -> HealthStatus.healthyIf(
                    tableStatus == TableStatus.ACTIVE,
                    "DynamoDB table '%s' was not ACTIVE: %s",
                    tableName(),
                    tableStatus
            ));
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
