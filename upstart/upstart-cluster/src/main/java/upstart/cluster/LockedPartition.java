package upstart.cluster;

import upstart.managedservices.ManagedServiceGraph;
import upstart.util.concurrent.services.ComposableService;

import javax.inject.Inject;

public final class LockedPartition<Partition extends ComposableService> extends ManagedServiceGraph {

  @Inject
  public LockedPartition(Partition partitionService, DistributedResourceLocker<PartitionId>.Lease lockLease) {
    super(partitionService, lockLease);
  }
}
