package upstart.services;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.Service;
import com.google.common.util.concurrent.ServiceManager;
import upstart.util.collect.MoreStreams;
import upstart.util.exceptions.MultiException;

public abstract class AggregateService extends NotifyingService {

  private ServiceManager serviceManager;

  protected abstract Iterable<? extends ComposableService> getComponentServices();

  @Override
  protected void doStart() {
    Listener stoppingListener = new StoppingListener();
    ImmutableList<? extends ComposableService> services = ImmutableList.copyOf(getComponentServices());
    for (ComposableService service : services) {
      service.addListener(stoppingListener, MoreExecutors.directExecutor());
    }
    serviceManager = new ServiceManager(services);
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

  private class StoppingListener extends Listener {
    @Override
    public void stopping(State from) {
      stop();
    }
  }
}
