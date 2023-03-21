package upstart.aws.test.dynamodb;

import static org.awaitility.Awaitility.await;
import static upstart.test.truth.CompletableFutureSubject.assertThat;

import com.google.common.truth.Truth;
import java.time.Duration;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.services.dynamodb.model.TimeToLiveStatus;
import upstart.config.UpstartModule;
import upstart.dynamodb.*;
import upstart.test.UpstartServiceTest;
import upstart.util.concurrent.Deadline;

@LocalDynamoDbTest
@UpstartServiceTest
public class DynamoTableInitializerTest extends UpstartModule {

  @Inject private TestInitializer testInitializer;
  @Inject private DynamoDbClientService dbService;

  @Override
  protected void configure() {
    super.configure();
    install(new DynamoDbModule());
    bind(DynamoDbNamespace.class)
        .toInstance(DynamoDbNamespace.environmentNamespace(upstartContext()));
    serviceManager().manage(TestInitializer.class);
  }

  @Test
  void ensureInitialization() {
    Deadline deadline = Deadline.withinSeconds(8);
    await()
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(
            () -> {
              assertThat(testInitializer.describeTable())
                  .doneWithin(deadline)
                  .completedWithResultSatisfying(
                      result ->
                          Truth.assertThat(
                              result.table().tableName().equals(testInitializer.tableName())));

              var ttlDesc =
                  assertThat(
                          dbService
                              .client()
                              .describeTimeToLive(b -> b.tableName(testInitializer.tableName())))
                      .doneWithin(deadline)
                      .completedNormally();
              Truth.assertThat(ttlDesc.timeToLiveDescription().timeToLiveStatus())
                  .isEqualTo(TimeToLiveStatus.ENABLED);
               Truth.assertThat(ttlDesc.timeToLiveDescription().attributeName()).isEqualTo("ttl");
            });
  }

  @Singleton
  public static class TestInitializer extends DynamoTableInitializer<TestBean> {

    @Inject
    public TestInitializer(DynamoDbClientService dbService, DynamoDbNamespace namespace) {
      super("test-table", TestBean.class, dbService, namespace);
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
