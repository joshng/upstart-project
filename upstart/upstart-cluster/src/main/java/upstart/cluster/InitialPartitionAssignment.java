package upstart.cluster;

import upstart.util.concurrent.services.NotifyingService;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * A pseudo-service whose startup reflects when an initial partition-assignment has been established.
 */
@Singleton
public class InitialPartitionAssignment extends NotifyingService {
  private final PartitionAssignmentCoordinator initializer;

  @Inject
  public InitialPartitionAssignment(PartitionAssignmentCoordinator initializer) {
    this.initializer = initializer;
  }

  @Override
  protected void doStart() {
    startWith(initializer.initialAssignmentStarted());
  }

  @Override
  protected void doCancelStart() {
    doStop();
  }

  @Override
  protected void doStop() {
    notifyStopped();
  }
}
