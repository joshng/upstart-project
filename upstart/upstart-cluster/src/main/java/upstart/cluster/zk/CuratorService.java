package upstart.cluster.zk;

import upstart.services.NotifyingService;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.state.ConnectionState;
import org.apache.curator.framework.state.ConnectionStateListener;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.curator.utils.ZookeeperFactory;
import org.apache.curator.x.async.AsyncCuratorFramework;
import org.apache.zookeeper.KeeperException;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Duration;
import java.util.Optional;

import static com.google.common.base.Preconditions.checkState;

@Singleton
public class CuratorService extends NotifyingService implements ConnectionStateListener {
  private final CuratorFramework framework;
  private final AsyncCuratorFramework asyncFramework;

  @Inject
  public CuratorService(ZkConfig zkConfig, ZookeeperFactory zookeeperFactory) {
    framework = newCuratorFramework(zkConfig, Optional.of(zookeeperFactory));
    asyncFramework = (AsyncCuratorFramework) AsyncCuratorFramework.wrap(framework)
            .with(zkConfig.defaultAsyncWatchMode());
    framework.getConnectionStateListenable().addListener(this);
  }

  public static CuratorFramework newCuratorFramework(ZkConfig zkConfig, Optional<ZookeeperFactory> zookeeperFactory) {
    CuratorFrameworkFactory.Builder builder = CuratorFrameworkFactory.builder()
            .connectString(zkConfig.connectionString())
            .retryPolicy(new ExponentialBackoffRetry(integerMillis(zkConfig.retryBaseSleep()), zkConfig.maxTryCount() - 1, 2000))
            .connectionTimeoutMs(integerMillis(zkConfig.connectionTimeout()))
            .sessionTimeoutMs(integerMillis(zkConfig.sessionTimeout()));

    zookeeperFactory.ifPresent(builder::zookeeperFactory);

    return builder.build();
  }

  public static CuratorFramework newCuratorFramework(ZkConfig zkConfig) {
    return newCuratorFramework(zkConfig, Optional.empty());
  }

  private static int integerMillis(Duration duration) {
    return (int) duration.toMillis();
  }

  public CuratorFramework getFramework() {
    return framework;
  }

  public AsyncCuratorFramework getAsyncFramework() {
    return asyncFramework;
  }

  @Override
  protected void doStart() {
    framework.start();
  }

  @Override
  protected void doStop() {
    try {
      framework.close();
      notifyStopped();
    } catch (Exception e) {
      notifyFailed(e);
    }
  }

  @Override
  public void stateChanged(CuratorFramework client, ConnectionState newState) {
    switch (newState) {
      case CONNECTED:
        if (state() == State.STARTING) notifyStarted();
        break;
      case LOST:
        if (isStoppable()) {
          notifyFailed(new KeeperException.SessionExpiredException());
        } else {
          notifyStopped();
        }
        break;
      case READ_ONLY:
        notifyFailed(new IllegalStateException("unexpected READ_ONLY state"));
        break;
    }
  }
}
