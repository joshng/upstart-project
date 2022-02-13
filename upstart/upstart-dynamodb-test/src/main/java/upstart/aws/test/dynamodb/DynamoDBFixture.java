package upstart.aws.test.dynamodb;

import com.amazonaws.services.dynamodbv2.local.main.ServerRunner;
import com.amazonaws.services.dynamodbv2.local.server.DynamoDBProxyServer;
import upstart.services.IdleService;
import upstart.test.AvailablePortAllocator;

public class DynamoDBFixture extends IdleService {

  private int port;
  private DynamoDBProxyServer server;
  private String endpoint;

  @Override
  protected void startUp() throws Exception {
    System.setProperty("sqlite4java.library.path", "native-libs");
    port = AvailablePortAllocator.allocatePort();
    server = ServerRunner.createServerFromCommandLineArgs(
            new String[]{"-inMemory", "-port", Integer.toString(port)});
    server.start();
    endpoint = "http://localhost:" + port;
  }

  public String endpoint() {
    return endpoint;
  }

  @Override
  protected void shutDown() throws Exception {
    server.stop();
    server.join();
  }
}
