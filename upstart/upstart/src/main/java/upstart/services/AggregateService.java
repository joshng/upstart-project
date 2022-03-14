package upstart.services;

import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.Service;
import com.google.common.util.concurrent.ServiceManager;
import upstart.util.MoreStreams;
import upstart.util.exceptions.MultiException;

public abstract class AggregateService extends NotifyingService {

  private ServiceManager serviceManager;

  protected abstract Iterable<? extends ComposableService> getComponentServices();

  @Override
  protected void doStart() {
    serviceManager = new ServiceManager(getComponentServices());
    serviceManager.addListener(new ServiceManager.Listener() {
      @Override
      public void healthy() {
        notifyStarted();
      }

      @Override
      public void stopped() {
        MoreStreams.foldLeft(
                        MultiException.Empty,
                        serviceManager.servicesByState().get(State.FAILED).stream()
                                .map(Service::failureCause), MultiException::with
                ).getCombinedThrowable()
                .ifPresentOrElse(
                                AggregateService.this::notifyFailed,
                                AggregateService.this::notifyStopped
                        );
      }

      @Override
      public void failure(Service service) {
        stop();
      }
    }, MoreExecutors.directExecutor());

    serviceManager.startAsync();
  }

  @Override
  protected void doCancelStart() {
    doStop();
  }

  @Override
  protected void doStop() {
    if (serviceManager != null) serviceManager.stopAsync();
  }

  @Override
  public String toString() {
    return super.toString() + "\n--" + serviceManager;
  }
}
