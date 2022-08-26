package upstart.cluster;

import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.MapBinder;
import upstart.config.UpstartModule;
import upstart.guice.AnnotationKeyedPrivateModule;
import upstart.guice.TypeLiterals;
import upstart.util.concurrent.services.ComposableService;
import upstart.guice.ImmutableNumbered;

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
    TypeLiteral<LockedPartition<Partition>> lockedPartitionType = TypeLiterals.getParameterized(LockedPartition.class, partitionServiceClass());
    int partitionCount = partitionCount();
    for (int i = 0; i < partitionCount; i++) {
      PartitionId partitionId = PartitionId.of(i);
      PartitionModule partitionModule = new PartitionModule(partitionId, partitionServiceModule, lockedPartitionType);
      install(partitionModule);
      partitionBinder.addBinding(partitionId).to(partitionModule.partitionKey);
    }
  }

  private class PartitionModule extends AnnotationKeyedPrivateModule {
    private final Module partitionModule;
    final PartitionId partitionId;
    final Key<LockedPartition<Partition>> partitionKey;

    PartitionModule(PartitionId partitionId, Module partitionServiceModule, TypeLiteral<LockedPartition<Partition>> lockedPartitionType) {
      super(ImmutableNumbered.of(partitionId.id()), lockedPartitionType.getType());
      this.partitionId = partitionId;
      this.partitionModule = partitionServiceModule;
      this.partitionKey = annotatedKey(lockedPartitionType);
//      exposeWithAnnotatedKey(lockedPartitionType);
    }

    @Override
    protected void configurePrivateScope() {
      bind(PartitionId.class).toInstance(partitionId);
      install(partitionModule);
    }

    @Provides
    DistributedResourceLocker<PartitionId>.Lease partitionLease(PartitionId partitionId, DistributedResourceLocker locker) {
      return locker.getActiveLeaseService(partitionId);
    }
  }
}
