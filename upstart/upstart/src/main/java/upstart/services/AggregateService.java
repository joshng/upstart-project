package upstart.services;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.Service;
import upstart.util.concurrent.CompletableFutures;
import upstart.util.exceptions.MultiException;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static com.google.common.base.Preconditions.checkState;

public abstract class AggregateService extends NotifyingService {

  private volatile List<? extends ComposableService> componentServices;

  protected abstract Iterable<? extends ComposableService> getComponentServices();

  protected AggregateService() {
    addListener(new Listener() {
      @Override
      public void failed(State from, Throwable failure) {
        // really shouldn't get here until all services have already stopped, but stop everything just in case
        componentServices.forEach(ComposableService::stop);
      }
    }, MoreExecutors.directExecutor());
  }

  @Override
  protected final void doStart() {
    componentServices = ImmutableList.copyOf(getComponentServices());
    ComponentServiceListener listener = new ComponentServiceListener();
    for (Service service : componentServices) {
      service.addListener(listener, MoreExecutors.directExecutor());
      // important: if any service is already started, then we've missed important transitions
      checkState(service.state() == State.NEW, "Service was already started", service);
    }

    startWith(perform(ComposableService::start));
  }

  @Override
  protected void doCancelStart() {
    doStop();
  }

  @Override
  protected final void doStop() {
    stopWith(perform(STOP));
  }

  private CompletableFuture<Void> perform(AsyncF<ComposableService, State> action) {
    return CompletableFutures.allOf(componentServices.stream().map(action))
            .whenComplete((__, t) -> checkHealthy(t));
  }

  private void checkHealthy(Throwable invocationException) {
    MultiException e = MultiException.Empty;
    if (invocationException != null) e = e.with(invocationException);
    for (Service service : componentServices) {
      if (service.state() == State.FAILED) {
        Throwable cause = service.failureCause();
        if (cause != invocationException) {
          e = e.with(cause);
        }
      }
    }

    e.throwRuntimeIfAny();
  }

  @Override
  public String toString() {
    if (componentServices != null && !componentServices.isEmpty()) {
      return Joiner.on("\n--")
              .appendTo(new StringBuilder(super.toString()).append("\n--"), componentServices)
              .toString();
    } else {
      return super.toString();
    }
  }

  private class ComponentServiceListener extends Listener {
    @Override
    public void stopping(State from) {
      triggerStop();
    }

    @Override
    public void terminated(State from) {
      triggerStop();
    }

    @Override
    public void failed(State from, Throwable failure) {
      triggerStop();
    }

    private void triggerStop() {
      if (isStoppable()) stop();
    }
  }
}
