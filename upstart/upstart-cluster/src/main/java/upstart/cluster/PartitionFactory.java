package upstart.cluster;

import upstart.services.ComposableService;

public interface PartitionFactory<PartitionId, Partition extends ComposableService> {
  Partition newPartition(PartitionId id);
}
