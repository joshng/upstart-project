package upstart.aws.test.dynamodb;

import com.amazonaws.services.dynamodbv2.local.main.ServerRunner;
import com.amazonaws.services.dynamodbv2.local.server.DynamoDBProxyServer;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import upstart.config.UpstartEnvironment;
import upstart.config.annotations.ConfigPath;
import upstart.services.IdleService;
import upstart.test.AvailablePortAllocator;
import upstart.test.systemStreams.SystemOutCaptor;
import upstart.util.concurrent.LazyReference;
import upstart.util.exceptions.MultiException;

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
      return DynamoDbClient.builder().endpointOverride(new URI(endpoint)).build();
    } catch (URISyntaxException e) {
      throw new AssertionError(e);
    }
  });

  @Override
  protected void startUp() throws Exception {
    String sqlite4javaPath = System.getProperty(SQLITE_4_JAVA_LIBRARY_PATH);
    if (sqlite4javaPath == null) {
      System.setProperty(
              SQLITE_4_JAVA_LIBRARY_PATH,
              UpstartEnvironment.loadAmbientConfigValue(LocalDynamoConfig.class).sqliteNativeLibs()
      );
    }

    port = AvailablePortAllocator.allocatePort();

    SystemOutCaptor outputCaptor = null;
    try (SystemOutCaptor captor = new SystemOutCaptor().startCapture()) {
      outputCaptor = captor;
      server = ServerRunner.createServerFromCommandLineArgs(
            new String[]{"-inMemory", "-port", Integer.toString(port)});
      server.start();
    } catch (Exception e) {
      System.out.print(outputCaptor.getCapturedString());
      throw e;
    }
    endpoint = "http://localhost:" + port;
  }

  public DynamoDbClient client() {
    return client.get();
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

  @ConfigPath("upstart.dynamodb-test")
  interface LocalDynamoConfig {
    String sqliteNativeLibs();
  }
}
