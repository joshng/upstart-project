package upstart.cluster;

import java.util.List;

public interface ClusterTransitionListener {
  void onNodesJoined(List<ClusterNodeId> nodeIds);

  void onNodeLeft(ClusterNodeId nodeId);
}
