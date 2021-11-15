package upstart.cluster;

import upstart.util.concurrent.BatchAccumulator;
import upstart.util.concurrent.Scheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

@Singleton
public class ClusterTransitionAccumulator implements ClusterTransitionListener {
  private static final Logger LOG = LoggerFactory.getLogger(ClusterTransitionAccumulator.class);

  private final BatchAccumulator<ClusterMembershipTransition> accumulator;

  @Inject
  public ClusterTransitionAccumulator(
          ClusterMembershipListener membershipListener,
          ClusterMembershipConfig config,
          Scheduler scheduler
  ) {
    this.accumulator = new BatchAccumulator<>(
            ClusterMembershipTransition::new,
            membershipListener::onClusterMembershipChanged,
            config.idleTransitionTimeout(),
            config.maxTransitionDelay(),
            scheduler
    );
  }

  @Override
  public void onNodesJoined(List<ClusterNodeId> nodeIds) {
    Duration delay = accumulator.accumulate(nodeIds, (transition, nodes) -> {
      for (ClusterNodeId node : nodes) {
        transition.nodeJoined(node);
      }
      return Optional.empty();
    });
    LOG.info("Discovered new node(s), waiting at least {}s to process: {}", delay.toMillis() / 1000, nodeIds);
  }

  @Override
  public void onNodeLeft(ClusterNodeId nodeId) {
    Duration delay = accumulator.accumulate(nodeId, (transition, node) -> {
      transition.nodeDeparted(node);
      return Optional.empty();
    });
    LOG.info("Discovered departed node, waiting at least {}s to process: {}", delay.toMillis() / 1000, nodeId);
  }
}

