package upstart.aws.test.dynamodb;

import com.amazonaws.services.dynamodbv2.local.main.ServerRunner;
import com.amazonaws.services.dynamodbv2.local.server.DynamoDBProxyServer;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import upstart.config.UpstartEnvironment;
import upstart.config.annotations.ConfigPath;
import upstart.test.UpstartTestBuilder;
import upstart.util.concurrent.services.IdleService;
import upstart.test.AvailablePortAllocator;
import upstart.test.systemStreams.SystemOutCaptor;
import upstart.util.concurrent.LazyReference;
import upstart.util.exceptions.MultiException;

import javax.annotation.Nonnull;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.stream.Stream;

public class DynamoDbFixture extends IdleService {

  public static final String SQLITE_4_JAVA_LIBRARY_PATH = "sqlite4java.library.path";
  private int port;
  private DynamoDBProxyServer server;
  private String endpoint;
  private final LazyReference<DynamoDbClient> client = LazyReference.from(() -> {
    try {
      return DynamoDbClient.builder()
              .endpointOverride(new URI(endpoint))
              .region(Region.US_EAST_1)
              .credentialsProvider(new FakeCredentialsProvider())
              .build();
    } catch (URISyntaxException e) {
      throw new AssertionError(e);
    }
  });

  private final LazyReference<DynamoDbEnhancedClient> enhancedClient = new LazyReference<>() {
    @Nonnull
    @Override
    protected DynamoDbEnhancedClient supplyValue() {
      return DynamoDbEnhancedClient.builder().dynamoDbClient(client()).build();
    }
  };

  @Override
  protected void startUp() throws Exception {
    String sqlite4javaPath = System.getProperty(SQLITE_4_JAVA_LIBRARY_PATH);
    if (sqlite4javaPath == null) {
      System.setProperty(SQLITE_4_JAVA_LIBRARY_PATH, "/tmp/sqlite4java/native-libs");
    }

    port = AvailablePortAllocator.allocatePort();

    SystemOutCaptor outputCaptor = null;
    try (SystemOutCaptor captor = new SystemOutCaptor().startCapture()) {
      outputCaptor = captor;
      server = ServerRunner.createServerFromCommandLineArgs(
            new String[]{"-inMemory", "-port", Integer.toString(port)});
      server.start();
    } catch (Exception e) {
      if (outputCaptor != null) System.out.print(outputCaptor.getCapturedString());
      throw e;
    }
    endpoint = "http://localhost:" + port;
  }

  public void configureLocalEndpoint(UpstartTestBuilder testBuilder) {
    testBuilder.overrideConfig("upstart.aws.dynamodb.endpoint", endpoint);
  }

  public DynamoDbClient client() {
    return client.get();
  }

  public DynamoDbEnhancedClient enhancedClient() {
    return enhancedClient.get();
  }

  public String endpoint() {
    return endpoint;
  }

  @Override
  protected void shutDown() throws Exception {
    MultiException.closeAll(Stream.concat(
            client.remove().stream(),
            Stream.of((AutoCloseable)() -> {
              server.stop();
              server.join();
            })
    )).throwRuntimeIfAny();
  }
}
