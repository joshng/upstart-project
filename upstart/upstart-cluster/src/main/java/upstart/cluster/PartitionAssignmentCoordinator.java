package upstart.cluster;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.Service;
import upstart.config.UpstartModule;
import upstart.util.concurrent.services.NotifyingService;
import upstart.util.concurrent.services.ComposableService;
import upstart.util.concurrent.NamedThreadFactory;
import upstart.util.concurrent.CompletableFutures;
import upstart.util.concurrent.FutureCell;
import upstart.util.concurrent.Promise;
import upstart.util.concurrent.Scheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Supplier;

import static com.google.common.base.Preconditions.checkState;

/**
 * Handles the assignment of a set of {@link PartitionId}s to the local node, based on consistent-hashing
 * applied to the set of {@link ClusterNodeId}s in the upstart cluster (tracked by {@link ClusterMembership}).
 * <p/>
 * For each locally-owned PartitionId, this service strives to ensure that exactly one corresponding
 * {@link ComposableService} is running somewhere the distributed upstart cluster. These services can attend to the
 * work associated with their PartitionIds with HA assurance, load balancing (assuming the work associated with each
 * PartitionIds is well-distributed), and best-effort mutual exclusion.
 * <p/>
 * However, no distributed solution can fully ensure mutual exclusion among partitioned processes, so we provide a
 * {@link FenceToken} whose {@link FenceToken#precedence precedence} can be used by collaborating processes to prevent
 * rogue commands from interfering with the ownership of the correct service.
 *
 * <p/>
 * The specific implementations for the partition-services and {@link DistributedResourceLocker locks} can be configured by
 * {@link UpstartModule#install(Class) installing} a subclass of
 * {@link PartitionCoordinationModule}.
 *
 *
 * @see PartitionCoordinationModule
 * @see ComposableService
 */
@Singleton
public class PartitionAssignmentCoordinator extends NotifyingService implements ClusterMembershipListener {
  private static final Logger LOG = LoggerFactory.getLogger(PartitionAssignmentCoordinator.class);
  private final ClusterNodeId localNodeId;
  private final ClusterMembership membership;

  private final FutureCell<AssignmentState> stateCell;

  private final Promise<AssignmentState> startupPromise = new Promise<>();
  private final Scheduler scheduler;
  private final DistributedResourceLocker<PartitionId> resourceLocker;
  private final Map<PartitionId, Provider<ComposableService>> partitionFactory;

  @SuppressWarnings({"rawtypes", "unchecked"})
  @Inject
  public PartitionAssignmentCoordinator(
          ClusterNodeId localNodeId,
          ClusterMembership membership,
          Scheduler scheduler,
          DistributedResourceLocker resourceLocker,
          Map<PartitionId, Provider<ComposableService>> partitionFactory
  ) {
    this.localNodeId = localNodeId;
    this.membership = membership;
    this.resourceLocker = resourceLocker;
    this.partitionFactory = partitionFactory;

    this.scheduler = scheduler;

    Executor stateThread = Executors.newSingleThreadExecutor(new NamedThreadFactory("part-coord").daemonize());

    stateCell = FutureCell.<AssignmentState>builder()
            .executor(stateThread)
            .onError(this::abort)
            .build(startupPromise);
  }

  protected CompletableFuture<? extends AssignmentState> buildInitialState() {
    return CompletableFuture.completedFuture(new AssignmentState(10000));
  }

  public CompletableFuture<?> initialAssignmentStarted() {
    return startupPromise.thenCompose(state -> state.initialAssignmentsCompletion);
  }

  @Override
  protected void doStart() {
    membership.assertMembership();
    membership.clusterShutdownRequestedFuture().thenRun(this::stop);
    startWith(trackFuture("Starting up partitions", () -> startupPromise.tryCompleteWith(this::buildInitialState)));
  }

  @Override
  protected void doStop() {
    stopWith(transact("Shutting down", AssignmentState::updateAssignedPartitions));
  }

  @Override
  public void onClusterMembershipChanged(ClusterMembershipTransition transition) {
    transact(transition.toString(), state -> state.onClusterMembershipChanged(transition));
  }

  @VisibleForTesting
  CompletableFuture<Set<PartitionId>> currentAssignment() {
    return startupPromise.thenApply(state -> ImmutableSet.copyOf(state.activePartitions.keySet()));
  }

  protected <T> CompletableFuture<T> transact(String description, Function<AssignmentState, ? extends CompletionStage<T>> action) {
      return stateCell.visitAsync(state -> trackFuture(description, () -> action.apply(state).toCompletableFuture()));
  }

  private <T> CompletableFuture<T> trackFuture(String description, Supplier<? extends CompletableFuture<T>> future) {
      return new FutureTracker(description).track(future);
  }

  private <F extends CompletableFuture<?>> F abortOnFailure(F future) {
    future.whenComplete((__, e) -> {
      if (e != null) abort(e);
    });
    return future;
  }

  private void abort(Throwable e) {
    LOG.error("Unexpected error, SERVICE WILL TERMINATE", e);
    notifyFailed(e);
  }

  protected class AssignmentState {
    private final ConsistentHashRing<ClusterNodeId> hashRing;
    private final Map<PartitionId, ComposableService> activePartitions = new HashMap<>();
    private final Promise<Void> initialAssignmentsCompletion = new Promise<>();

    AssignmentState(int hashRingNodesPerMember) {
      hashRing = new ConsistentHashRing<>(hashRingNodesPerMember, (node, hasher) -> hasher.putString(node.sessionId(), StandardCharsets.UTF_8));
    }

    CompletableFuture<Void> onClusterMembershipChanged(ClusterMembershipTransition transition) {
      LOG.info("{} Commencing cluster membership transition:\n  {}", localNodeId, transition);
      hashRing.addWorkers(transition.joinedNodeIds.stream());
      hashRing.removeWorkers(transition.departedNodeIds.stream());

      return updateAssignedPartitions().thenRun(() -> LOG.info("COMPLETED transition: {}", transition));
    }

    private CompletableFuture<Void> updateAssignedPartitions() {
      Set<PartitionId> newAssignment = isRunning()
              ? hashRing.computeAssignments(localNodeId, partitionFactory.keySet(), PartitionId::partitionHashCode)
              : ImmutableSet.of();

      Set<PartitionId> oldAssignment = activePartitions.keySet();
      if (newAssignment.equals(oldAssignment)) return CompletableFutures.nullFuture();

      Set<PartitionId> additions = Sets.difference(newAssignment, oldAssignment).immutableCopy();
      Set<PartitionId> removals = Sets.difference(oldAssignment, newAssignment).immutableCopy();

      LOG.info("{} updating partition assignment: starting {}, stopping {}", localNodeId, additions.size(), removals.size());

      CompletableFuture<Void> partitionsReleased = CompletableFutures.allOf(
              removals.stream().map(this::relinquishPartition)
      );

      // we don't prevent subsequent transitions while partitions are starting up, because this can cause distributed deadlock:
      // we may need to interrupt the startup of a partition if a subsequent assignment needs to relinquish it
      CompletableFuture<Void> partitionsAdded = startPartitions(additions);
      if (!initialAssignmentsCompletion.isDone()) {
        initialAssignmentsCompletion.completeWith(CompletableFuture.allOf(partitionsReleased, partitionsAdded));
      }

      return partitionsReleased;
    }

    private CompletableFuture<Void> startPartitions(Collection<PartitionId> partitionIds) {
      if (!isRunning() || partitionIds.isEmpty()) {
        return CompletableFutures.nullFuture();
      }

      Object description = LOG.isTraceEnabled() ? partitionIds : partitionIds.size();
      LOG.info("{} Preparing to process partition(s): {}", localNodeId, description);

      return CompletableFutures.allOf(partitionIds.stream().map(partitionId -> {
        resourceLocker.prepareLeaseService(partitionId);
        ComposableService partition = partitionFactory.get(partitionId).get();
        partition.addListener(new PartitionFailureListener(partitionId), MoreExecutors.directExecutor());
        checkState(activePartitions.putIfAbsent(partitionId, partition) == null, "Partition was already started", partitionId);
        return abortOnFailure(trackFuture("Starting partition " + partitionId, partition::start));
      }));
    }

    private CompletableFuture<?> relinquishPartition(PartitionId partitionId) {
      LOG.debug("{} Preparing to release partition {}", localNodeId, partitionId);
      ComposableService partition = activePartitions.remove(partitionId);
      return partition != null
        ? partition.stop()
        : CompletableFutures.nullFuture();
    }

    private class PartitionFailureListener extends Service.Listener {
      private final PartitionId partitionId;

      PartitionFailureListener(PartitionId partitionId) {
        this.partitionId = partitionId;
      }

      @Override
      public void failed(State from, Throwable failure) {
        abort(new RuntimeException("Partition " + partitionId + " failed while " + from, failure));
      }
    }
  }


  private static final AtomicInteger ACTION_ID = new AtomicInteger();

  private class FutureTracker {
    private final String description;
    private final Instant start = scheduler.now();

    FutureTracker(String description) {
      this.description = String.format("ActionId[%05d] %s", ACTION_ID.incrementAndGet(), description);
    }

    <F extends CompletableFuture<?>> F track(Supplier<F> starter) {
      LOG.info("Commencing action: {}", description);

      F future = starter.get();

      ScheduledFuture<?> warnFuture;
      if (!future.isDone()) {
        Duration deadlockWarnPeriod = Duration.ofSeconds(20);
        warnFuture = scheduler.scheduleAtFixedRate(deadlockWarnPeriod, deadlockWarnPeriod, () -> {
            if (!future.isDone()) {
              LOG.warn("...{} Still waiting for action to complete after {}s... deadlocked?? {}",
                      localNodeId, durationSeconds(), description);
            }
        });
      } else {
        warnFuture = null;
      }
      future.whenComplete((__, e) -> {
        if (warnFuture != null) warnFuture.cancel(false);
        LOG.info("...{} Action completed after {}s: {}", localNodeId, durationSeconds(), description);
      });
      return future;
    }

    private long durationSeconds() {
      return Duration.between(start, scheduler.now()).getSeconds();
    }
  }
}
