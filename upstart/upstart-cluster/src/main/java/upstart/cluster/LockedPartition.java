package upstart.cluster;

import upstart.services.ManagedServiceGraph;
import upstart.services.ComposableService;

import javax.inject.Inject;

public final class LockedPartition<Partition extends ComposableService> extends ManagedServiceGraph {

  @Inject
  public LockedPartition(Partition partitionService, DistributedResourceLocker<PartitionId>.Lease lockLease) {
    super(partitionService, lockLease);
  }
}
