package upstart.cluster.test;

import upstart.cluster.zk.CuratorService;
import upstart.cluster.zk.ZkConfig;
import upstart.config.EnvironmentConfigFixture;
import upstart.config.TestConfigBuilder;
import upstart.test.SingletonExtension;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.test.TestingServer;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Optional;

public class ZookeeperFixture implements EnvironmentConfigFixture {
  private final TestingServer server = new TestingServer(true);

  ZookeeperFixture() throws Exception {
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

  void start() throws Exception {
  }

  void stop() {
    try {
      server.stop();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public static ZookeeperFixture getInstance(ExtensionContext context) {
    return SingletonExtension.getRequiredContextFrom(ZookeeperExtension.class, context);
  }
}
