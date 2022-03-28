package upstart.services;

import com.google.common.collect.Sets;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.Service;
import upstart.util.collect.Optionals;
import upstart.util.reflect.Reflect;
import upstart.util.concurrent.CompletableFutures;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static com.google.common.base.Preconditions.checkState;

/**
 * A wrapper for an underlying {@link Service} which coordinates its lifecycle state-changes to participate in a graph
 * (DAG) of other interdependent services.
 *
 * This wrapper will:
 * <ul>
 * <li>refrain from starting its underlying service until all dependencies have started</li>
 * <li>refrain from stopping its underlying service until all dependents have stopped</li>
 * <li>reflect the stopping/terminated/failed state of the underlying service accurately, so that the rest of the
 *     system can respond as soon as the underlying service stops itself.</li>
 * </ul>
 */
public class LifecycleCoordinator extends NotifyingService {
  private static final String LOG_CATEGORY_PREFIX = LifecycleCoordinator.class.getName() + ".";
  private final Logger logger;

  private final ComposableService underlyingService;
  private final Set<LifecycleCoordinator> dependentServices = Sets.newHashSet();
  private final Set<LifecycleCoordinator> requiredServices = Sets.newHashSet();

  LifecycleCoordinator(Service underlyingService) {
    underlyingService.addListener(new UnderlyingServiceListener(), MoreExecutors.directExecutor());
    checkState(underlyingService.state() == State.NEW, "Underlying service was not NEW!", underlyingService);
    this.underlyingService = ComposableService.enhance(underlyingService);
    logger = getLifecycleLogger(underlyingService);
  }

  private static Logger getLifecycleLogger(Service underlyingService) {
    String serviceName = Optionals.asInstance(underlyingService, ComposableService.class)
            .map(ComposableService::serviceName)
            .orElseGet(() -> Reflect.getUnenhancedClass(underlyingService.getClass()).getName());
    return LoggerFactory.getLogger(LOG_CATEGORY_PREFIX + serviceName);
  }

  void addRequiredService(LifecycleCoordinator requiredService) {
    assert state() == State.NEW : "Service was no longer NEW";
    if (requiredServices.add(requiredService)) {
      requiredService.addDependentService(this);
    }
  }

  private void addDependentService(LifecycleCoordinator dependentService) {
    dependentServices.add(dependentService);
  }

  public Service getUnderlyingService() {
    return underlyingService;
  }

  public Set<LifecycleCoordinator> getDependentServices() {
    return Collections.unmodifiableSet(dependentServices);
  }

  public Set<LifecycleCoordinator> getRequiredServices() {
    return Collections.unmodifiableSet(requiredServices);
  }

  @Override
  protected void onStartupCanceled() {
    logger.warn("startup canceled");
    notifyStarted();
  }

  @Override
  protected void doStart() {
    logger.debug("Wrapper starting... {}", underlyingService);
    startWith(
            CompletableFutures.allOf(requiredServices.stream().map(BaseComposableService::getStartedFuture))
                    .thenCompose(ignored -> underlyingService.start())
                    .thenAccept(state -> logger.info("Started ({}): {}", state, underlyingService))
    );
  }

  @Override
  protected void doStop() {
    logger.debug("Wrapper stopping... {}", underlyingService);

    CompletableFuture<?> readyToStop;
    if (underlyingService.isStoppable()) {
      // if the service is "stoppable" -- even if it's still NEW or STARTING -- then we're careful to stop
      // it only after its dependencies are terminated. Otherwise, dependencies could transition to RUNNING on
      // another thread before we manage to stop it below.
      readyToStop = CompletableFutures.allOf(dependentServices.stream().map(ComposableService::getTerminationFuture));
    } else {
      logger.info("Stopped by itself ({}): {}", underlyingService.state(), underlyingService);
      readyToStop = CompletableFutures.nullFuture();
    }

    failWith(readyToStop
            .thenCompose(__ -> STOP.apply(underlyingService))
            .thenAccept(state -> {
              switch (state) {
                case TERMINATED:
                  logger.info("Stopped ({}): {}", state, underlyingService);
                  notifyStopped();
                  break;
                case FAILED: // if FAILED, then notifyFailed and logging will have happened elsewhere
                  break;
                default:
                  throw new IllegalStateException("Expected stopped service to be TERMINATED or FAILED, but was " + state);
              }
            }));
  }

  @Override
  public String toString() {
    return "Service{" + underlyingService + "}";
  }

  private class UnderlyingServiceListener extends Listener {
    @Override public void starting() {
      if (state() == State.NEW) {
        // we really shouldn't be here; something started this service behind our back
        notifyFailed(new IllegalStateException("Service was externally started: " + underlyingService));
        underlyingService.stop(); // don't leave anything running unattended
      }
    }

    @Override public void stopping(State from) {
      stop();
    }

    @Override
    public void terminated(State from) {
      // Services can transition to TERMINATED without STOPPING
      // (eg, when an ExecutionThreadService returns from `run`)
      stop();
    }

    @Override public void failed(State from, Throwable failure) {
      logger.error("FAILED, service will stop: {}", underlyingService, CompletableFutures.unwrapExecutionException(failure));
      notifyFailed(failure);
    }
  }
}
