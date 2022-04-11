package upstart.cluster;

import upstart.util.concurrent.services.ComposableService;

public interface PartitionFactory<PartitionId, Partition extends ComposableService> {
  Partition newPartition(PartitionId id);
}
