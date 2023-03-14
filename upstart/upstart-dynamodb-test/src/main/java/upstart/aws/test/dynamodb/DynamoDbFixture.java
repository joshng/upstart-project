package upstart.aws.test.dynamodb;

import com.amazonaws.services.dynamodbv2.local.main.ServerRunner;
import com.amazonaws.services.dynamodbv2.local.server.DynamoDBProxyServer;
import org.junit.jupiter.api.extension.ExtensionContext;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import upstart.UpstartService;
import upstart.config.EnvironmentConfigFixture;
import upstart.config.TestConfigBuilder;
import upstart.dynamodb.DynamoTableInitializer;
import upstart.test.AvailablePortAllocator;
import upstart.test.UpstartApplicationSandbox;
import upstart.test.systemStreams.SystemOutCaptor;
import upstart.util.concurrent.LazyReference;
import upstart.util.concurrent.services.IdleService;
import upstart.util.exceptions.MultiException;

import javax.annotation.Nonnull;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Optional;
import java.util.stream.Stream;

public class DynamoDbFixture extends IdleService implements EnvironmentConfigFixture, UpstartApplicationSandbox.Initializer {

  public static final String SQLITE_4_JAVA_LIBRARY_PATH = "sqlite4java.library.path";
  public static final Region REGION = Region.US_WEST_2;
  private int port;
  private DynamoDBProxyServer server;
  private String endpoint;
  private final LazyReference<DynamoDbClient> client = LazyReference.from(() -> {
    try {
      return DynamoDbClient.builder()
              .endpointOverride(new URI(endpoint))
              .region(REGION)
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

  @Override
  public void applyEnvironmentValues(TestConfigBuilder<?> config, Optional<ExtensionContext> testExtensionContext) {
    config.overrideConfig("""
                          upstart.aws.dynamodb {
                            endpoint: "%s"
                            region: %s
                          }
                          """.formatted(endpoint, REGION));
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

  @Override
  public void initializeSandbox(UpstartService.Builder builder) {
    builder.installModule(DynamoTableInitializer.PROVISIONED_RESOURCE_TYPE.startupProvisioningModule());
  }
}
