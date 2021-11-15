package upstart.cluster.zk;

import com.google.common.collect.ImmutableList;
import upstart.cluster.ClusterNodeId;
import upstart.cluster.ClusterTransitionListener;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.cache.ChildData;
import org.apache.curator.framework.recipes.cache.PathChildrenCacheListener;
import org.apache.curator.utils.ZKPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;
import java.util.stream.Collectors;

@Singleton
public class ZkClusterWatcher extends BasePathChildrenCacheService {
    private static final Logger LOG = LoggerFactory.getLogger(ZkClusterWatcher.class);
    private final ZkClusterMembership membership;
    private final ClusterTransitionListener accumulator;

    @Inject
    public ZkClusterWatcher(ZkClusterMembership membership, ClusterTransitionListener accumulator, CuratorFramework syncFramework) {
        super(syncFramework);
        this.membership = membership;
        this.accumulator = accumulator;
    }

    @Override
    protected String rootPath() {
        return membership.getClusterRootPath();
    }

    @Override
    protected PathChildrenCacheListener listener() {
        return new ChildrenListener() {
            @Override
            public void onInitialized(List<ChildData> data) {
                accumulator.onNodesJoined(data.stream()
                        .map(this::nodeId)
                        .collect(Collectors.toList()));
            }

            @Override
            public void onChildAdded(ChildData childData) {
                accumulator.onNodesJoined(ImmutableList.of(nodeId(childData)));
            }

            @Override
            public void onChildUpdated(ChildData childData) {
                // not supposed to happen..?
                LOG.warn("Node data changed?? {}", childData);
                stop();
            }

            @Override
            public void onChildRemoved(ChildData childData) {
                accumulator.onNodeLeft(nodeId(childData));
                if (childData.getPath().equals(membership.getMembershipNodePath())) {
                    //expect ClusterMembership to be the last thing to shut down
                    LOG.error("Received nodeLeft notification for myself. Expected shutdown order violated.");
                    stop();
                }
            }

            @Override
            public void onConnectionLost() {
                stop();
            }

            ClusterNodeId nodeId(ChildData data) {
                return ClusterNodeId.of(ZKPaths.getNodeFromPath(data.getPath()));
            }
        };
    }
}
