package upstart.cluster;

public interface ClusterMembershipListener {
  void onClusterMembershipChanged(ClusterMembershipTransition transition);
}
