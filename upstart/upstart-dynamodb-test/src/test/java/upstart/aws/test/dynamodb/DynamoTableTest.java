package upstart.aws.test.dynamodb;

import static com.google.common.truth.Truth.assertThat;
import static org.awaitility.Awaitility.await;
import static upstart.test.truth.CompletableFutureSubject.assertThat;

import com.google.common.truth.Truth;
import java.time.Duration;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.services.dynamodb.model.TimeToLiveStatus;
import upstart.config.UpstartModule;
import upstart.dynamodb.*;
import upstart.test.UpstartLibraryServiceTest;
import upstart.util.concurrent.Deadline;

@LocalDynamoDbTest
@UpstartLibraryServiceTest
public class DynamoTableTest extends UpstartModule {

  public static final String TEST_TABLE_NAME = "test-table";
  @Inject private TestTableReader testInitializer;
  @Inject private DynamoDbClientService dbService;
  @Inject private @Named(TEST_TABLE_NAME) DynamoTable table;
  private DynamoDbNamespace namespace;

  @Override
  protected void configure() {
    super.configure();
    install(new DynamoDbModule());
    namespace = DynamoDbNamespace.environmentNamespace(upstartContext());
    install(new DynamoTable.TableModule(TEST_TABLE_NAME, namespace));
  }

  @Test
  void ensureInitialization() {
    Deadline deadline = Deadline.withinSeconds(8);
    await()
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(
            () -> {
              assertThat(table.describeTable().thenApply(r -> r.table().tableName()))
                  .doneWithin(deadline)
                  .completedWithResultSatisfying(Truth::assertThat)
                  .isEqualTo(table.tableName());

              var ttlDesc =
                  assertThat(
                          dbService
                              .client()
                              .describeTimeToLive(b -> b.tableName(namespace.tableName(TEST_TABLE_NAME))))
                      .doneWithin(deadline)
                      .completedNormally();
              assertThat(ttlDesc.timeToLiveDescription().timeToLiveStatus())
                  .isEqualTo(TimeToLiveStatus.ENABLED);
              assertThat(ttlDesc.timeToLiveDescription().attributeName()).isEqualTo("ttl");
            });
  }

  @Singleton
  public static class TestTableReader extends DynamoTableDao<TestBean, TestBean> {

    @Inject
    public TestTableReader(@Named(TEST_TABLE_NAME) DynamoTable table) {
      super(TestBean.class, table, ItemExtractor.identity());
    }
  }

  @DynamoDbBean
  public static class TestBean {
    private String partitionKey;
    private long ttl;

    @DynamoDbPartitionKey
    public String getPartitionKey() {
      return partitionKey;
    }

    public void setPartitionKey(String partitionKey) {
      this.partitionKey = partitionKey;
    }

    @TimeToLiveAttribute
    public long getTtl() {
      return ttl;
    }

    public void setTtl(long ttl) {
      this.ttl = ttl;
    }
  }
}
