package upstart.cluster.test;

import upstart.test.BaseSingletonParameterResolver;
import upstart.test.SingletonServiceExtension;
import upstart.test.UpstartExtension;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

public class ZookeeperExtension extends BaseSingletonParameterResolver<ZookeeperFixture> implements SingletonServiceExtension<ZookeeperFixture> {
  public ZookeeperExtension() {
    super(ZookeeperFixture.class);
  }

  @Override
  public ZookeeperFixture createService(ExtensionContext extensionContext) throws Exception {
    return new ZookeeperFixture();
  }
}
