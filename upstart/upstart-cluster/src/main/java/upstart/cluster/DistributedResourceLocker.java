package upstart.cluster;

import upstart.util.concurrent.services.NotifyingService;
import upstart.util.concurrent.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import static com.google.common.base.Preconditions.checkState;

/**
 * Must provide a mechanism for "locking" access to resources within the {@link ClusterMembership cluster}, to ensure
 * that each resource is only "owned" by a single node at a time.
 */
public abstract class DistributedResourceLocker<ResourceId> {
  private static final Logger LOG = LoggerFactory.getLogger(DistributedResourceLocker.class);
  protected static final CompletableFuture<Optional<FenceToken>> CANCELLED_LOCK_FUTURE = CompletableFuture.completedFuture(Optional.empty());
  private final ClusterNodeId nodeId;
  private final Map<ResourceId, Lease> activeLeases = new ConcurrentHashMap<>();

  protected DistributedResourceLocker(ClusterNodeId nodeId) {
    this.nodeId = nodeId;
  }

  /**
   * Prepares a service which, when running, ensures the given resource is locked for exclusive access.
   * This method merely prepares the {@link Lease} service; it must be {@link Lease#start started} to request the lock.
   * Once started, the service will only transition to {@link NotifyingService.State#RUNNING} when the lock is held, and
   * then to {@link NotifyingService.State#TERMINATED} only when the lock has been released.
   */
  public Lease prepareLeaseService(ResourceId resourceId) {
    return new Lease(resourceId, leaseStrategy(resourceId, nodeId));
  }

  /**
   * Returns the active {@link Lease} for the given {@link ResourceId}. Must only be called after
   * {@link #prepareLeaseService} for the given ResourceId, and before the Lease has been {@link Lease#stop stopped}.
   */
  Lease getActiveLeaseService(ResourceId resourceId) {
    Lease lease = activeLeases.get(resourceId);
    checkState(lease != null, "LeaseService was not active", resourceId);
    return lease;
  }

  protected abstract Strategy leaseStrategy(ResourceId resourceId, ClusterNodeId nodeId);

  public interface Strategy {
    CompletableFuture<Optional<FenceToken>> requestLock();

    CompletableFuture<?> relinquishLock();
  }

  public final class Lease extends NotifyingService {
    private final ResourceId resourceId;
    private final Strategy lockStrategy;
    private final Promise<Optional<FenceToken>> tokenPromise = new Promise<>();

    Lease(ResourceId ResourceId, Strategy lockStrategy) {
      this.resourceId = ResourceId;
      this.lockStrategy = lockStrategy;
      checkState(activeLeases.putIfAbsent(resourceId, this) == null, "LeaseService was already active", resourceId);
    }

    public CompletableFuture<Optional<FenceToken>> tokenFuture() {
      return tokenPromise;
    }

    @Override
    protected final void doStart() {
      startWith(tokenPromise.completeWith(lockStrategy.requestLock()));
    }

    @Override
    protected void onStartupCanceled() {
      LOG.debug("aborting lock for {}", resourceId);
      tokenPromise.complete(Optional.empty());
    }

    @Override
    protected void doStop() {
      checkState(activeLeases.remove(resourceId, this), "Lease wasn't active", resourceId);
      LOG.debug("releasing lock for {}", resourceId);
      stopWith(lockStrategy.relinquishLock());
    }

    @Override
    public String serviceName() {
      return super.serviceName() + "-" + resourceId;
    }
  }
}
