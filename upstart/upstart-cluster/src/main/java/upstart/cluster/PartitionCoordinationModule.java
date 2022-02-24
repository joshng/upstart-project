package upstart.cluster;

import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.PrivateModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.multibindings.MapBinder;
import com.google.inject.util.Types;
import upstart.config.UpstartModule;
import upstart.services.ComposableService;
import upstart.guice.ImmutableNumbered;

import java.lang.reflect.ParameterizedType;

public abstract class PartitionCoordinationModule<Partition extends ComposableService> extends UpstartModule {
  private final Key<? extends PartitionAssignmentCoordinator> coordinatorKey;

  protected PartitionCoordinationModule() {
    coordinatorKey = coordinatorKey();
  }

  protected Key<? extends PartitionAssignmentCoordinator> coordinatorKey() {
    return Key.get(PartitionAssignmentCoordinator.class);
  }

  protected abstract int partitionCount();
  protected abstract Class<Partition> partitionServiceClass();
  protected abstract Module partitionServiceModule();
  protected abstract Class<? extends DistributedResourceLocker> partitionResourceLockerClass();

  @Override
  protected void configure() {
    serviceManager()
            .manage(coordinatorKey)
            .manage(InitialPartitionAssignment.class)
    ;

    bind(ClusterTransitionListener.class).to(ClusterTransitionAccumulator.class);
    bindConfig(ClusterMembershipConfig.class);
    bind(ClusterMembershipListener.class).to(coordinatorKey);
    bind(DistributedResourceLocker.class).to(partitionResourceLockerClass()).in(Scopes.SINGLETON);

    MapBinder<PartitionId, ComposableService> partitionBinder = MapBinder.newMapBinder(binder(), PartitionId.class, ComposableService.class);
    Module partitionServiceModule = partitionServiceModule();
    ParameterizedType lockedPartitionType = Types.newParameterizedType(LockedPartition.class, partitionServiceClass());
    int partitionCount = partitionCount();
    for (int i = 0; i < partitionCount; i++) {
      PartitionId partitionId = PartitionId.of(i);
      PartitionModule partitionModule = new PartitionModule(partitionId, partitionServiceModule, lockedPartitionType);
      install(partitionModule);
      partitionBinder.addBinding(partitionId).to(partitionModule.partitionKey);
    }
  }

  private class PartitionModule extends PrivateModule {
    private final Module partitionModule;
    final PartitionId partitionId;
    final Key<LockedPartition<Partition>> partitionKey;

    @SuppressWarnings("unchecked")
    PartitionModule(PartitionId partitionId, Module partitionModule, ParameterizedType lockedPartitionType) {
      this.partitionId = partitionId;
      partitionKey = (Key<LockedPartition<Partition>>) Key.get(lockedPartitionType, ImmutableNumbered.of(partitionId.id()));
      this.partitionModule = partitionModule;
    }

    @Override
    protected void configure() {
      bind(PartitionId.class).toInstance(partitionId);
      bind(partitionKey).to(Key.get(partitionKey.getTypeLiteral()));
      install(partitionModule);
      expose(partitionKey);
    }

    @Provides
    DistributedResourceLocker<PartitionId>.Lease partitionLease(PartitionId partitionId, DistributedResourceLocker locker) {
      return locker.getActiveLeaseService(partitionId);
    }
  }
}
