package upstart.dynamodb;

import com.google.common.collect.MoreCollectors;
import com.google.inject.Key;
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
import java.util.Collections;
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
  private final Set<DynamoTableMapper> registeredApis = new HashSet<>();
  private final DynamoDbNamespace namespace;

  @Inject
  public DynamoTable(
          @PrivateBinding String tableNameSuffix,
          @PrivateBinding DynamoDbNamespace namespace,
          DynamoDbClientService dbService
  ) {
    this.namespace = namespace;
    tableName = this.namespace.tableName(tableNameSuffix);
    this.dbService = dbService;
  }

  public synchronized void registerApi(DynamoTableMapper reader) {
    checkState(state() == State.NEW, "Tried to register after startup");
    registeredApis.add(reader);
  }

  @Override
  protected synchronized CompletableFuture<?> startUp() throws Exception {
    return super.startUp();
  }

  public Set<DynamoTableMapper> registeredApis() {
    return Collections.unmodifiableSet(registeredApis);
  }

  public CompletableFuture<TableStatus> getTableStatus() {
    return describeTable().thenApply(resp -> resp.table().tableStatus());
  }

  public CompletableFuture<DescribeTableResponse> describeTable() {
    return dbService.describeTable(tableName);
  }

  protected <T> DynamoDbAsyncTable<T> enhancedTable(TableSchema<T> schema) {
    return dbService.enhancedClient().table(tableName, schema);
  }


  @Override
  public String resourceId() {
    return tableName;
  }

  @Override
  public String ownerEnvironment() {
    return namespace.namespace();
  }

  @Override
  public ResourceType resourceType() {
    return PROVISIONED_RESOURCE_TYPE;
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

  @Override
  public Promise<Void> waitUntilProvisioned() {
    return dbService.waitUntilTableExists(tableName, Duration.ofSeconds(5), promise -> {
      if (wasStartupCanceled()) {
        promise.completeExceptionally(new ShutdownException("Startup was canceled"));
      } else {
        LOG.warn("Waiting for table '{}' to be ready...", tableName);
      }
    }).toVoid();
  }

  @Override
  public Promise<Void> provisionIfNotExists() {
    Promise<Void> created = dbService.ensureTableCreated(createTableRequest());
    return getTtlAttribute()
            .map(ttlAttr -> created.thenReplaceFuture(() -> applyTtl(ttlAttr)))
            .orElse(created);
  }

  private CreateTableRequest createTableRequest() {
    OperationContext operationContext = DefaultOperationContext.create(tableName);

    List<CreateTableRequest> createTableRequests = registeredApis.stream()
            .map(api -> buildCreateTableRequest(api, api.tableSchema(), operationContext))
            .toList();

    return switch (createTableRequests.size()) {
      case 0 -> throw new IllegalStateException("No CreateTableRequests");
      case 1 -> createTableRequests.get(0);
      default -> mergeCreateTableRequest(createTableRequests);
    };
  }

  private static <T> CreateTableRequest buildCreateTableRequest(
          DynamoTableMapper api,
          TableSchema<T> tableSchema,
          OperationContext operationContext
  ) {
    CreateTableEnhancedRequest enhancedRequest = api.prepareCreateTableRequest(CreateTableEnhancedRequest.builder()).build();
    return CreateTableOperation.<T>create(enhancedRequest)
            .generateRequest(tableSchema, operationContext, null);
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

  private CreateTableRequest mergeCreateTableRequest(List<CreateTableRequest> apiRequests) {
    CreateTableRequest request = CreateTableRequest.builder().tableName(tableName)
            .attributeDefinitions(apiRequests.stream()
                                          .flatMap(r -> r.attributeDefinitions().stream()).distinct().toList())
            .keySchema(apiRequests.stream()
                               .flatMap(r -> r.keySchema().stream()).distinct().toList())
            .globalSecondaryIndexes(apiRequests.stream()
                                            .flatMap(r -> r.globalSecondaryIndexes().stream()).distinct().toList())
            .localSecondaryIndexes(apiRequests.stream()
                                           .flatMap(r -> r.localSecondaryIndexes().stream()).distinct().toList())
            .tableClass(apiRequests.stream()
                                .map(CreateTableRequest::tableClass).distinct().collect(MoreCollectors.onlyElement()))
            .streamSpecification(apiRequests.stream()
                                         .map(CreateTableRequest::streamSpecification)
                                         .filter(Objects::nonNull)
                                         .distinct()
                                         .collect(MoreCollectors.toOptional())
                                         .orElse(null))
            .billingMode(apiRequests.stream()
                                 .map(CreateTableRequest::billingMode).distinct()
                                 .collect(MoreCollectors.onlyElement()))
            .provisionedThroughput(apiRequests.stream()
                                           .map(CreateTableRequest::provisionedThroughput)
                                           .filter(Objects::nonNull)
                                           .distinct()
                                           .collect(MoreCollectors.toOptional())
                                           .orElse(null))
            .tags(apiRequests.stream().flatMap(r -> r.tags().stream()).distinct().toList())
            .build();
    return request;
  }

  private Optional<String> getTtlAttribute() {
    return registeredApis.stream().map(DynamoTableMapper::getTtlAttribute).flatMap(Optional::stream).collect(MoreCollectors.toOptional());
  }

  @Override
  public String serviceName() {
    return "DynamoTable{" + tableName + "}";
  }

  public static class TableModule extends UpstartModule {
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
