package upstart.cluster;


import java.util.HashSet;
import java.util.Set;

public class ClusterMembershipTransition {
  public final Set<ClusterNodeId> joinedNodeIds = new HashSet<>();
  public final Set<ClusterNodeId> departedNodeIds = new HashSet<>();

  public boolean isEmpty() {
    return joinedNodeIds.isEmpty() && departedNodeIds.isEmpty();
  }

  void nodeJoined(ClusterNodeId nodeId) {
    if (!departedNodeIds.remove(nodeId)) {
      joinedNodeIds.add(nodeId);
    }
  }

  void nodeDeparted(ClusterNodeId nodeId) {
    if (!joinedNodeIds.remove(nodeId)) {
      departedNodeIds.add(nodeId);
    }
  }

  @Override
  public String toString() {
    return "ClusterMembershipTransition{" +
            "joinedNodeIds=" + joinedNodeIds +
            ", departedNodeIds=" + departedNodeIds +
            '}';
  }
}
