package upstart.cluster.test;

import upstart.cluster.zk.ZkClusterAdministrator;
import upstart.util.concurrent.CompletableFutures;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.x.async.AsyncCuratorFramework;
import org.junit.jupiter.api.extension.AfterTestExecutionCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.time.Duration;

public class UpstartClusterShutdownExtension implements AfterTestExecutionCallback {
  @Override
  public void afterTestExecution(ExtensionContext context) throws Exception {
    UpstartTestClusterBuilder configurator = UpstartTestClusterBuilder.getInstance(context);
    try (CuratorFramework curatorService = ZookeeperFixture.getInstance(context).newCuratorService()) {

      ZkClusterAdministrator administrator = new ZkClusterAdministrator(AsyncCuratorFramework.wrap(curatorService));
      CompletableFutures.allOf(configurator.getClusterIds().stream().map(administrator::requestClusterShutdown)).join();
      configurator.awaitShutdown(Duration.ofSeconds(10));
    }
  }
}
