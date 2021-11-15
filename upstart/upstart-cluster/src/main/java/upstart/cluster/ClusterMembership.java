package upstart.cluster;

import upstart.services.BaseComposableService;
import upstart.services.ComposableService;

import java.util.concurrent.CompletableFuture;

import static com.google.common.base.Preconditions.checkState;

/**
 * A marker interface symbolizing membership in the cluster: managed {@link BaseComposableService}s that inject this
 * interface will be started after the local node has joined the cluster.
 * <p/>
 * Systems that require membership confirmation prior to initializing should @Inject ClusterMembership
 * (and are encouraged to {@link #assertMembership} where appropriate, just to be safe).
 */
public interface ClusterMembership extends ComposableService {
  default void assertMembership() {
    checkState(isRunning(), "ClusterMembership is not currently established", state());
  }

  CompletableFuture<?> clusterShutdownRequestedFuture();
}
