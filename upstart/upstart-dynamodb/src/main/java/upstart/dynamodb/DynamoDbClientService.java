package upstart.dynamodb;

import com.google.common.util.concurrent.MoreExecutors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbAsyncTable;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedAsyncClient;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClientBuilder;
import software.amazon.awssdk.services.dynamodb.model.DescribeTableResponse;
import software.amazon.awssdk.services.dynamodb.model.DynamoDbException;
import software.amazon.awssdk.services.dynamodb.model.ResourceInUseException;
import software.amazon.awssdk.services.dynamodb.model.TableAlreadyExistsException;
import software.amazon.awssdk.services.dynamodb.model.TableStatus;
import upstart.aws.Aws;
import upstart.aws.AwsAsyncClientFactory;
import upstart.aws.BaseAwsAsyncClientService;
import upstart.managedservices.ServiceLifecycle;
import upstart.util.concurrent.services.ThreadPoolService;
import upstart.util.concurrent.BlockingBoundedActor;
import upstart.util.concurrent.CompletableFutures;
import upstart.util.concurrent.NamedThreadFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.google.common.base.Preconditions.checkState;

@Singleton
@ServiceLifecycle(ServiceLifecycle.Phase.Infrastructure)
public class DynamoDbClientService extends BaseAwsAsyncClientService<DynamoDbAsyncClient, DynamoDbAsyncClientBuilder> {
  private static final Logger LOG = LoggerFactory.getLogger(DynamoDbClientService.class);
  public static final int MAX_ITEMS_PER_DYNAMODB_BATCH = 25;
  private final Executor directExecutor = MoreExecutors.directExecutor();
  private final DynamoDbConfig config;
  private final BlockingBoundedActor tableCreationActor = new BlockingBoundedActor(10);
  private DynamoDbEnhancedAsyncClient enhancedClient;


  @Inject
  public DynamoDbClientService(
          @Aws(Aws.Service.DynamoDB) AwsAsyncClientFactory clientFactory,
          DynamoThreadPoolService completionExecutor,
          DynamoDbConfig config
  ) {
    super(clientFactory, completionExecutor);
    this.config = config;
  }

  @Override
  protected DynamoDbAsyncClientBuilder asyncClientBuilder() {
    return DynamoDbAsyncClient.builder();
  }

  @Override
  protected void startUp() throws Exception {
    super.startUp();
    enhancedClient = DynamoDbEnhancedAsyncClient.builder().dynamoDbClient(client()).build();
  }

  public <T> CompletableFuture<DynamoDbAsyncTable<T>> ensureTableCreated(String tableName, TableSchema<T> tableSchema) {
    return tableCreationActor.requestAsync(() -> {
      DynamoDbAsyncTable<T> table = enhancedClient.table(tableName, tableSchema);
      // TODO: dynamo doesn't support concurrent DDL operations, but what exception is thrown if a different table is creating?
      LOG.debug("Initiating table creation: {}", tableName);
      return CompletableFutures.recover(
              table.createTable(),
              DynamoDbException.class,
              e -> (e instanceof ResourceInUseException) || (e instanceof TableAlreadyExistsException),
              e -> null
      ).thenComposeAsync(ignored -> pollTableStatus(
                                 tableName,
                                 CompletableFuture.delayedExecutor(
                                         config.tableCreationPollPeriod().toMillis(),
                                         TimeUnit.MILLISECONDS,
                                         directExecutor
                                 )
                         )
      ).thenApply(ignored -> table);
    }, directExecutor);
  }

  public CompletableFuture<DescribeTableResponse> describeTable(String tableName) {
    return client().describeTable(b -> b.tableName(tableName));
  }

  public DynamoDbEnhancedAsyncClient enhancedClient() {
    return enhancedClient;
  }

  private CompletableFuture<?> pollTableStatus(String tableName, Executor pollDelayExecutor) {
    LOG.debug("Polling table status: {}", tableName);
    return describeTable(tableName).thenCompose(resp -> {
      TableStatus tableStatus = resp.table().tableStatus();
      if (tableStatus == TableStatus.CREATING) {
        LOG.info("Waiting for dynamodb table '{}' creation...", tableName);
        return CompletableFutures.sequence(
                CompletableFuture.supplyAsync(() -> pollTableStatus(tableName, pollDelayExecutor), pollDelayExecutor)
        );
      } else {
        checkState(
                tableStatus == TableStatus.ACTIVE,
                "Dynamodb table %s was not active: %s",
                tableName,
                tableStatus
        );
        return CompletableFutures.nullFuture();
      }
    });
  }

  @Singleton
  @ServiceLifecycle(ServiceLifecycle.Phase.Infrastructure)
  static class DynamoThreadPoolService extends ThreadPoolService {

    protected DynamoThreadPoolService() {
      super(Duration.ofSeconds(5));
    }

    @Override
    protected ExecutorService buildExecutorService() {
      return Executors.newCachedThreadPool(new NamedThreadFactory("dynamo-cb"));
    }
  }
}
