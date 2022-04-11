package upstart.dynamodb;

import com.google.common.util.concurrent.MoreExecutors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbAsyncTable;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedAsyncClient;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.model.DescribeTableRequest;
import software.amazon.awssdk.services.dynamodb.model.DynamoDbException;
import software.amazon.awssdk.services.dynamodb.model.ResourceInUseException;
import software.amazon.awssdk.services.dynamodb.model.TableAlreadyExistsException;
import software.amazon.awssdk.services.dynamodb.model.TableStatus;
import upstart.aws.Aws;
import upstart.aws.AwsAsyncClientFactory;
import upstart.util.concurrent.services.IdleService;
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
public class DynamoDbClientService extends IdleService {
  private static final Logger LOG = LoggerFactory.getLogger(DynamoDbClientService.class);
  private final AwsAsyncClientFactory clientFactory;
  private final Executor completionExecutor;
  private final Executor directExecutor = MoreExecutors.directExecutor();
  private final DynamoDbConfig config;
  private final BlockingBoundedActor tableCreationActor = new BlockingBoundedActor(10);
  private DynamoDbEnhancedAsyncClient db;
  private DynamoDbAsyncClient client;


  @Inject
  public DynamoDbClientService(
          @Aws(Aws.Service.DynamoDB) AwsAsyncClientFactory clientFactory,
          DynamoThreadPoolService completionExecutor,
          DynamoDbConfig config
  ) {
    this.clientFactory = clientFactory;
    this.completionExecutor = completionExecutor;
    this.config = config;
  }

  @Override
  protected void startUp() throws Exception {
    client = clientFactory.configureAsyncClientBuilder(DynamoDbAsyncClient.builder(), completionExecutor).build();
    db = DynamoDbEnhancedAsyncClient.builder().dynamoDbClient(client).build();
  }

  public <T> CompletableFuture<DynamoDbAsyncTable<T>> ensureTableCreated(String tableName, TableSchema<T> tableSchema) {
    return tableCreationActor.requestAsync(() -> {
      DynamoDbAsyncTable<T> table = db.table(tableName, tableSchema);
      DescribeTableRequest describeTable = DescribeTableRequest.builder().tableName(tableName).build();
      // TODO: dynamo doesn't support concurrent DDL operations, but what exception is thrown if a different table is creating?
      LOG.debug("Initiating table creation: {}", tableName);
      return CompletableFutures.recover(
              table.createTable(),
              DynamoDbException.class,
              e -> (e instanceof ResourceInUseException) || (e instanceof TableAlreadyExistsException),
              e -> null
      ).thenComposeAsync(ignored -> pollTableStatus(
                      describeTable,
                      CompletableFuture.delayedExecutor(config.tableCreationPollPeriod().toMillis(), TimeUnit.MILLISECONDS, directExecutor))
              ).thenApply(ignored -> table);
    }, directExecutor);
  }

  private CompletableFuture<?> pollTableStatus(
          DescribeTableRequest request,
          Executor pollDelayExecutor
  ) {
    LOG.debug("Polling table status: {}", request.tableName());
    String tableName = request.tableName();
    return client.describeTable(request).thenCompose(resp -> {
      TableStatus tableStatus = resp.table().tableStatus();
      if (tableStatus == TableStatus.CREATING) {
        LOG.info("Waiting for dynamodb table '{}' creation...", tableName);
        return CompletableFutures.sequence(
                CompletableFuture.supplyAsync(() -> pollTableStatus(request, pollDelayExecutor), pollDelayExecutor)
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

  @Override
  protected void shutDown() throws Exception {
    if (client != null) client.close();
  }

  @Singleton
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
