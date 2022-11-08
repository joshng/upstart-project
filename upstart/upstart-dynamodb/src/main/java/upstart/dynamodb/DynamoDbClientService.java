package upstart.dynamodb;

import com.google.common.util.concurrent.MoreExecutors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbAsyncTable;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedAsyncClient;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.CreateTableEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClientBuilder;
import software.amazon.awssdk.services.dynamodb.model.DescribeTableResponse;
import software.amazon.awssdk.services.dynamodb.model.DynamoDbException;
import software.amazon.awssdk.services.dynamodb.model.ResourceInUseException;
import software.amazon.awssdk.services.dynamodb.model.TableAlreadyExistsException;
import upstart.aws.AwsAsyncClientService;
import upstart.managedservices.ServiceLifecycle;
import upstart.util.concurrent.Promise;
import upstart.util.concurrent.services.ThreadPoolService;
import upstart.util.concurrent.BlockingBoundedActor;
import upstart.util.concurrent.NamedThreadFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.google.common.base.Preconditions.checkState;

@Singleton
@ServiceLifecycle(ServiceLifecycle.Phase.Infrastructure)
public class DynamoDbClientService {
  private static final Logger LOG = LoggerFactory.getLogger(DynamoDbClientService.class);
  public static final int MAX_ITEMS_PER_DYNAMODB_BATCH = 25;
  private static final Executor directExecutor = MoreExecutors.directExecutor();
  private final BlockingBoundedActor tableCreationActor = new BlockingBoundedActor(10);
  private DynamoDbEnhancedAsyncClient enhancedClient;
  private final DynamoDbAsyncClient client;

  @Inject
  public DynamoDbClientService(DynamoDbAsyncClient client) {
    this.enhancedClient = DynamoDbEnhancedAsyncClient.builder()
            .dynamoDbClient(client)
            .build();
    this.client = client;
  }

//  @Override
//  protected void startUp() throws Exception {
//    super.startUp();
//    enhancedClient = DynamoDbEnhancedAsyncClient.builder().dynamoDbClient(client()).build();
//  }

  public DynamoDbAsyncClient client() {
    return client;
  }


  public DynamoDbEnhancedAsyncClient enhancedClient() {
    return enhancedClient;
  }

  public <T> Promise<DynamoDbAsyncTable<T>> ensureTableCreated(
          String tableName,
          TableSchema<T> tableSchema,
          CreateTableEnhancedRequest request
  ) {
    DynamoDbAsyncTable<T> table = enhancedClient.table(tableName, tableSchema);
    return tableCreationActor.requestAsync(() -> {
      // TODO: dynamo doesn't support concurrent DDL operations, but what exception is thrown if a different table is creating?
      LOG.debug("Initiating table creation: {}", tableName);
      return Promise.of(table.createTable(request))
              .recover(
                      DynamoDbException.class,
                      e -> (e instanceof ResourceInUseException) || (e instanceof TableAlreadyExistsException),
                      e -> null
              ).thenReplaceFuture(() -> {
                                    LOG.debug("Polling table status: {}", tableName);
                                    return client().waiter().waitUntilTableExists(b -> b.tableName(tableName));
                                  }
              ).thenReplace(table);
    }, directExecutor).recover(Exception.class, e -> {
      throw new RuntimeException("Error ensuring table readiness: " + tableName, e);
    });
	;
  }

  public CompletableFuture<DescribeTableResponse> describeTable(String tableName) {
    return client().describeTable(b -> b.tableName(tableName));

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
