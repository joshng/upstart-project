package upstart.util.concurrent.services;

import com.google.common.util.concurrent.Service;
import upstart.util.collect.Optionals;
import upstart.util.functions.AsyncFunction;

import java.util.concurrent.CompletableFuture;

import static com.google.common.base.Preconditions.checkState;

/**
 * A process that can be {@link #start}ed and {@link #stop}ped, based on the guava {@link Service} library.
 * This interface extends the guava {@link Service} API by offering {@link CompletableFuture}s to simplify the
 * composition of dependent behavior when {@link #state} transitions to {@link State#RUNNING}
 * or {@link State#TERMINATED TERMINATED}/{@link State#FAILED FAILED}.
 * <p/>
 * Generally implemented by subclassing one of the various subtypes of {@link BaseComposableService}.
 *
 * @see Service
 * @see NotifyingService
 * @see IdleService
 * @see InitializingService
 * @see ExecutionThreadService
 * @see ScheduledService
 */
public interface ComposableService extends Service {
  AsyncFunction<ComposableService, State> STOP_QUIETLY = input -> input.stop()
          .exceptionally(e -> State.FAILED);

  static ComposableService enhance(Service service) {
    return Optionals.asInstance(service, ComposableService.class)
            .orElseGet(() -> new AdapterService(service));
  }

  CompletableFuture<State> getStartedFuture();

  CompletableFuture<State> getStoppedFuture();

  CompletableFuture<State> start();

  CompletableFuture<State> stop();

  default CompletableFuture<State> getTerminationFuture() {
    return getStoppedFuture().exceptionally(e -> State.FAILED);
  }

  String serviceName();

  default boolean isStoppable() {
    return state().compareTo(State.RUNNING) <= 0;
  }

  default boolean noLongerRunning() {
    return !isStoppable();
  }

  default boolean notYetStarted() {
    return state().compareTo(State.RUNNING) < 0;
  }

  default void checkRunning() {
    checkState(isRunning(), "%s: Service is not running", this);
  }

  default void checkNotYetStarted() {
    checkState(notYetStarted(), "%s: Service has already started", this);
  }

  class AdapterService extends BaseComposableService<Service> {
    private AdapterService(Service delegate) {
      super(delegate);
    }

    @Override
    public String toString() {
      return delegate().toString();
    }
  }
}
