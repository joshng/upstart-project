package upstart.cluster.test;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.test.TestingServer;
import org.junit.jupiter.api.extension.ExtensionContext;
import upstart.cluster.zk.CuratorService;
import upstart.cluster.zk.ZkConfig;
import upstart.config.EnvironmentConfigFixture;
import upstart.config.TestConfigBuilder;
import upstart.test.SingletonExtension;
import upstart.util.concurrent.services.IdleService;

import java.util.Optional;

public class ZookeeperFixture extends IdleService implements EnvironmentConfigFixture {
  private final TestingServer server = new TestingServer(false);

  ZookeeperFixture() throws Exception {
  }

  @Override
  protected void startUp() throws Exception {
    server.start();
  }

  @Override
  protected void shutDown() throws Exception {
    server.stop();
  }

  public String connectString() {
    return server.getConnectString();
  }

  @Override
  public void applyEnvironmentValues(TestConfigBuilder<?> config, Optional<ExtensionContext> testExtensionContext) {
    config.overrideConfig("upstart.cluster.zk", zkConfig());
  }

  public ZkConfig zkConfig() {
    return ZkConfig.builder()
            .connectionString(connectString())
            .build();
  }

  public CuratorFramework newCuratorService() {
    CuratorFramework framework = CuratorService.newCuratorFramework(zkConfig());
    framework.start();
    return framework;
  }

  public static ZookeeperFixture getInstance(ExtensionContext context) {
    return SingletonExtension.getRequiredContextFrom(ZookeeperExtension.class, context);
  }
}
