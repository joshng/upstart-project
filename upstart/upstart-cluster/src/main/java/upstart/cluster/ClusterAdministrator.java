package upstart.cluster;

import upstart.cluster.zk.ClusterId;

import java.util.concurrent.CompletableFuture;

public interface ClusterAdministrator {
  CompletableFuture<?> requestClusterShutdown(ClusterId clusterId);
}
