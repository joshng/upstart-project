package upstart.dynamodb;

import com.google.common.collect.MoreCollectors;
import com.google.inject.Key;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Names;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbAsyncTable;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedAsyncClient;
import software.amazon.awssdk.enhanced.dynamodb.OperationContext;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.internal.operations.CreateTableOperation;
import software.amazon.awssdk.enhanced.dynamodb.internal.operations.DefaultOperationContext;
import software.amazon.awssdk.enhanced.dynamodb.model.CreateTableEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.model.CreateTableRequest;
import software.amazon.awssdk.services.dynamodb.model.DescribeTableResponse;
import software.amazon.awssdk.services.dynamodb.model.TableStatus;
import software.amazon.awssdk.services.dynamodb.model.TimeToLiveStatus;
import upstart.aws.SdkPojoSerializer;
import upstart.config.UpstartModule;
import upstart.guice.PrivateBinding;
import upstart.guice.UpstartPrivateModule;
import upstart.healthchecks.HealthCheck;
import upstart.healthchecks.HealthChecker;
import upstart.provisioning.BaseProvisionedResource;
import upstart.provisioning.ProvisionedResource;
import upstart.util.collect.PersistentMap;
import upstart.util.concurrent.Promise;
import upstart.util.concurrent.ShutdownException;
import upstart.util.strings.NamingStyle;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.lang.annotation.Annotation;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static com.google.common.base.Preconditions.checkState;

@Singleton
public class DynamoTable extends BaseProvisionedResource implements HealthCheck {
  private static final Logger LOG = LoggerFactory.getLogger(DynamoTable.class);
  public static final ResourceType PROVISIONED_RESOURCE_TYPE = new ResourceType("dynamodb_table");

  private final DynamoDbClientService dbService;
  private final String tableName;
  private final Set<DynamoTableDao<?, ?>> readers = new HashSet<>();

  @Inject
  public DynamoTable(
          @PrivateBinding String tableNameSuffix,
          @PrivateBinding DynamoDbNamespace namespace,
          DynamoDbClientService dbService
  ) {
    tableName = namespace.tableName(tableNameSuffix);
    this.dbService = dbService;
  }

  public synchronized void registerReader(DynamoTableDao<?, ?> reader) {
    checkState(state() == State.NEW, "Cannot register readers after startup");
    readers.add(reader);
  }

  @SuppressWarnings("unchecked")
  public CompletableFuture<TableStatus> getTableStatus() {
    return describeTable().thenApply(resp -> resp.table().tableStatus());
  }

  public CompletableFuture<DescribeTableResponse> describeTable() {
    return dbService.describeTable(tableName);
  }

  private Promise<Void> initTable(Promise<?> tablePromise) {
    return tablePromise.thenReplaceFuture(this::onTableInitializedAsync);
  }

  protected Promise<Void> onTableInitializedAsync() {
    return Promise.allOf(readers.stream().map(reader -> reader.onTableInitialized(this)));
  }

  protected <T> DynamoDbAsyncTable<T> enhancedTable(TableSchema<T> schema) {
    return dbService.enhancedClient().table(tableName, schema);
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
    PersistentMap<String, Object> tableSpec = SdkPojoSerializer.serialize(createTableRequest(), NamingStyle.LowerCamelCaseSplittingAcronyms);

    // this AWS SDK doesn't support the inline TTL attribute specification (?!), so we have to add it manually
    return getTtlAttribute()
            .map(ttlAttr -> tableSpec.plus("timeToLiveSpecification", PersistentMap.of(
                    "attributeName", ttlAttr,
                    "enabled", true
                    )
            )).orElse(tableSpec);
  }

  private CreateTableRequest createTableRequest() {
    OperationContext operationContext = DefaultOperationContext.create(tableName);

    List<CreateTableRequest> createTableRequests = readers.stream()
            .map(reader -> buildCreateTableRequest(reader, operationContext))
            .toList();

    return switch (createTableRequests.size()) {
      case 0 -> throw new IllegalStateException("No CreateTableRequests");
      case 1 -> createTableRequests.get(0);
      default -> mergeCreateTableRequest(createTableRequests);
    };
  }

  private CreateTableRequest mergeCreateTableRequest(List<CreateTableRequest> readerRequests) {
    CreateTableRequest.Builder builder = CreateTableRequest.builder();
    CreateTableRequest request = builder.tableName(tableName)
            .attributeDefinitions(readerRequests.stream()
                                          .flatMap(r -> r.attributeDefinitions().stream()).distinct().toList())
            .keySchema(readerRequests.stream()
                               .flatMap(r -> r.keySchema().stream()).distinct().toList())
            .globalSecondaryIndexes(omitEmptyList(
                    readerRequests.stream().flatMap(r -> r.globalSecondaryIndexes().stream()).distinct().toList()))
            .localSecondaryIndexes(omitEmptyList(
                    readerRequests.stream().flatMap(r -> r.localSecondaryIndexes().stream()).distinct().toList()))
            .tableClass(readerRequests.stream()
                                .map(CreateTableRequest::tableClass).distinct().collect(MoreCollectors.onlyElement()))
            .streamSpecification(readerRequests.stream()
                                         .map(CreateTableRequest::streamSpecification)
                                         .filter(Objects::nonNull)
                                         .distinct()
                                         .collect(MoreCollectors.toOptional())
                                         .orElse(null))
            .billingMode(readerRequests.stream()
                                 .map(CreateTableRequest::billingMode).distinct()
                                 .collect(MoreCollectors.onlyElement()))
            .provisionedThroughput(readerRequests.stream()
                                           .map(CreateTableRequest::provisionedThroughput)
                                           .filter(Objects::nonNull)
                                           .distinct()
                                           .collect(MoreCollectors.toOptional()).orElse(null))
            .tags(readerRequests.stream().flatMap(r -> r.tags().stream()).distinct().toList())
            .build();
    return request;
  }

  private static <T> List<T> omitEmptyList(List<T> list) {
    return Optional.of(list)
            .filter(l -> !l.isEmpty())
            .orElse(null);
  }

  static <T> CreateTableRequest buildCreateTableRequest(
          DynamoTableDao<T, ?> reader,
          OperationContext operationContext
  ) {
    CreateTableEnhancedRequest enhancedRequest = reader.prepareCreateTableRequest(CreateTableEnhancedRequest.builder()).build();
    return CreateTableOperation.<T>create(enhancedRequest)
            .generateRequest(reader.tableSchema(), operationContext, null);
  }

  @Override
  public Promise<Void> provisionIfNotExists() {
    Promise<Void> created = dbService.ensureTableCreated(createTableRequest());
    return getTtlAttribute()
            .map(ttlAttr -> created.thenReplaceFuture(() -> applyTtl(ttlAttr)))
            .orElse(created);
  }

  @Override
  public Promise<Void> waitUntilProvisioned() {
    return initTable(dbService.waitUntilTableExists(tableName, Duration.ofSeconds(5), promise -> {
      if (wasStartupCanceled()) {
        promise.completeExceptionally(new ShutdownException("Startup was canceled"));
      } else {
        LOG.warn("Waiting for table '{}' to be ready...", tableName);
      }
    }));
  }

  private Optional<String> getTtlAttribute() {
    return readers.stream().map(DynamoTableDao::getTtlAttribute).flatMap(Optional::stream).collect(MoreCollectors.toOptional());
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

  public DynamoDbEnhancedAsyncClient enhancedClient() {
    return dbService.enhancedClient();
  }

  public DynamoDbAsyncClient client() {
    return dbService.client();
  }

  public CompletableFuture<DescribeTableResponse> describeTable(String tableName) {
    return dbService.describeTable(tableName);
  }

  @Override
  public String serviceName() {
    return "DynamoTable{" + tableName + "}";
  }

  public static class TableModule extends UpstartModule {
    private static final TypeLiteral<DynamoTableDao<?, ?>> TABLE_READER_TYPE = new TypeLiteral<>() {};
    public static final TypeLiteral<Set<DynamoTableDao<?, ?>>> TABLE_READER_SET_TYPE = new TypeLiteral<>(){};
    private final Key<DynamoTable> key;
    private final DynamoDbNamespace namespace;
    private final String tableNameSuffix;

    public TableModule(String tableNameSuffix, DynamoDbNamespace namespace) {
      this(tableNameSuffix, Key.get(DynamoTable.class, Names.named(tableNameSuffix)), namespace);
    }
    public TableModule(String tableNameSuffix, Annotation annotation, DynamoDbNamespace namespace) {
      this(tableNameSuffix, Key.get(DynamoTable.class, annotation), namespace);
    }

    public TableModule(String tableNameSuffix, Key<DynamoTable> key, DynamoDbNamespace namespace) {
      super(key, tableNameSuffix, namespace);
      this.key = key;
      this.tableNameSuffix = tableNameSuffix;
      this.namespace = namespace;
    }

    @Override
    protected void configure() {
      install(new DynamoDbModule());
      ProvisionedResource.bindProvisionedResource(binder(), key);
      HealthChecker.bindHealthChecks(binder(), key);
      install(new UpstartPrivateModule() {
        @Override
        protected void configure() {
          bind(key).to(DynamoTable.class).asEagerSingleton();
          expose(key);
          bindPrivateBinding(String.class).toInstance(tableNameSuffix);
          bindPrivateBinding(DynamoDbNamespace.class).toInstance(namespace);
        }
      });
    }
  }
}
