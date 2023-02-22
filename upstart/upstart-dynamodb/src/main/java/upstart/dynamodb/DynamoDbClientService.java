package upstart.dynamodb;

import com.google.common.util.concurrent.MoreExecutors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbAsyncTable;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedAsyncClient;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.CreateTableEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.model.DescribeTableResponse;
import software.amazon.awssdk.services.dynamodb.model.DynamoDbException;
import software.amazon.awssdk.services.dynamodb.model.ResourceInUseException;
import software.amazon.awssdk.services.dynamodb.model.TableAlreadyExistsException;
import upstart.managedservices.ServiceLifecycle;
import upstart.util.concurrent.BlockingBoundedActor;
import upstart.util.concurrent.Promise;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

@Singleton
@ServiceLifecycle(ServiceLifecycle.Phase.Infrastructure)
public class DynamoDbClientService {
  private static final Logger LOG = LoggerFactory.getLogger(DynamoDbClientService.class);
  public static final int MAX_ITEMS_PER_DYNAMODB_BATCH = 25;
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

  public DynamoDbAsyncClient client() {
    return client;
  }


  public DynamoDbEnhancedAsyncClient enhancedClient() {
    return enhancedClient;
  }

  public Promise<Void> ensureTableCreated(
          String tableName,
          TableSchema<?> tableSchema,
          CreateTableEnhancedRequest request
  ) {
    DynamoDbAsyncTable<?> table = enhancedClient.table(tableName, tableSchema);
    return tableCreationActor.requestAsync(() -> {
              // TODO: dynamo doesn't support concurrent DDL operations, but what exception is thrown if a different table is creating?
              LOG.debug("Initiating table creation: {}", table.tableName());
              return Promise.of(table.createTable(request))
                      .recover(
                              DynamoDbException.class,
                              e -> (e instanceof ResourceInUseException) || (e instanceof TableAlreadyExistsException),
                              e -> null
                      );
            }, MoreExecutors.directExecutor())
            .recover(Exception.class, e -> {
              throw new RuntimeException("Error ensuring table readiness: " + tableName, e);
            });
  }

  public <T> Promise<DynamoDbAsyncTable<T>> waitUntilTableExists(
          String tableName,
          TableSchema<T> tableSchema,
          Duration incompleteStatusTimeout,
          Consumer<? super Promise<DynamoDbAsyncTable<T>>> incompleteStatusCallback
  ) {
    DynamoDbAsyncTable<T> table = enhancedClient.table(tableName, tableSchema);
    return new IncompleteFuturePoller<>(
            Promise.of(client().waiter().waitUntilTableExists(b -> b.tableName(table.tableName()))).thenReplace(table),
            incompleteStatusCallback,
            incompleteStatusTimeout
    ).start();
  }

  public CompletableFuture<DescribeTableResponse> describeTable(String tableName) {
    return client().describeTable(b -> b.tableName(tableName));
  }

  static class IncompleteFuturePoller<F extends Future<?>> implements Runnable {
    private final Executor callbackExecutor;
    private final F future;
    private final Consumer<? super F> incompleteStatusCallback;

    IncompleteFuturePoller(F future,
            Consumer<? super F> incompleteStatusCallback,
            Duration incompleteStatusTimeout
    ) {
      this.future = future;
      this.incompleteStatusCallback = incompleteStatusCallback;
      callbackExecutor = CompletableFuture.delayedExecutor(incompleteStatusTimeout.toNanos(), TimeUnit.NANOSECONDS);
    }

    public F start() {
      if (!future.isDone()) callbackExecutor.execute(this);
      return future;
    }

    @Override
    public void run() {
      if (!future.isDone()) {
        incompleteStatusCallback.accept(future);
        start();
      }
    }
  }
}
