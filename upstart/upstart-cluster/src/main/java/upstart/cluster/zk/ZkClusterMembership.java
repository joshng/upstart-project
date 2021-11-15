package upstart.cluster.zk;


import com.google.common.primitives.Ints;
import com.google.common.util.concurrent.MoreExecutors;
import upstart.cluster.ClusterMembership;
import upstart.cluster.ClusterNodeId;
import upstart.services.NotifyingService;
import upstart.util.concurrent.CompletableFutures;
import upstart.util.concurrent.Promise;
import org.apache.curator.utils.ZKPaths;
import org.apache.curator.x.async.AsyncStage;
import org.apache.curator.x.async.api.AsyncCuratorFrameworkDsl;
import org.apache.curator.x.async.api.CreateOption;
import org.apache.curator.x.async.api.DeleteOption;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.WatchedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.EnumSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Establishes membership in the upstart cluster.
 * <p/>
 * Also watches the "cluster-epoch" value, and triggers a shutdown by calling {@link #stop} if the epoch changes.
 */
@Singleton
public class ZkClusterMembership extends NotifyingService implements ClusterMembership {
  private static final Logger LOG = LoggerFactory.getLogger(ZkClusterMembership.class);
  private static final String CLUSTER_PARENT_PATH = "/PipCluster/";
  private static final byte[] GENESIS_EPOCH = Ints.toByteArray(0);
  private final ClusterId clusterId;
  private final AsyncCuratorFrameworkDsl asyncFramework;
  private final ClusterNodeId nodeId;
  private final Promise<Object> shutdownPromise = new Promise<>();
  private String membershipNodePath;
  private String clusterRootPath;

  @Inject
  public ZkClusterMembership(ClusterId clusterId, ClusterNodeId nodeId, AsyncCuratorFrameworkDsl asyncFramework) {
    this.clusterId = clusterId;
    this.asyncFramework = asyncFramework;
    this.nodeId = nodeId;
    addListener(new Listener() {
      @Override
      public void running() {
        LOG.info("Joined cluster as {}", membershipNodePath);
      }
    }, MoreExecutors.directExecutor());
  }

  @Override
  public CompletableFuture<?> clusterShutdownRequestedFuture() {
    return shutdownPromise;
  }

  public String getClusterRootPath() {
    assertMembership();
    return clusterRootPath;
  }

  public String getMembershipNodePath() {
    return membershipNodePath;
  }

  @Override
  protected void doStart() {
    startWith(getCurrentEpoch()
            .thenCompose(epochBytes -> {
              String epoch = Integer.toString(Ints.fromByteArray(epochBytes));
              clusterRootPath = ZKPaths.makePath(CLUSTER_PARENT_PATH, clusterId.value(), "Epochs", epoch);
              // TODO: should this use curators "withProtection" feature to handle lost replies?
              membershipNodePath = ZKPaths.makePath(clusterRootPath, nodeId.sessionId());
              return asyncFramework.create()
                      .withOptions(EnumSet.of(CreateOption.createParentsAsContainers), CreateMode.EPHEMERAL)
//                            .withOptions(EnumSet.of(CreateOption.createParentsIfNeeded), CreateMode.EPHEMERAL)
                      .forPath(membershipNodePath)
                      .toCompletableFuture();
            }));
  }

  private CompletableFuture<byte[]> getCurrentEpoch() {
    // try to create a genesis-epoch, ignoring if an epoch value already exists
    String epochPath = getEpochPath(clusterId);
    return CompletableFutures.recover(asyncFramework.create().withOptions(EnumSet.of(CreateOption.createParentsAsContainers))
                    .forPath(epochPath, GENESIS_EPOCH),
            KeeperException.NodeExistsException.class,
            e -> epochPath
    ).thenCompose(__ -> {
      AsyncStage<byte[]> epochData = asyncFramework.watched().getData().forPath(epochPath);
      CompletionStage<WatchedEvent> epochChanged = epochData.event();
      epochChanged.thenRun(() -> LOG.info("Cluster epoch changed, notifying for shutdown"));
      shutdownPromise.completeWith(epochChanged);
      return epochData;
    });
  }

  public static String getEpochPath(ClusterId clusterId) {
    return ZKPaths.makePath(CLUSTER_PARENT_PATH, clusterId.value(), "CurrentEpoch");
  }

  @Override
  protected void doStop() {
    stopWith(asyncFramework.delete().withOptions(EnumSet.of(DeleteOption.quietly)).forPath(membershipNodePath));
  }
}

