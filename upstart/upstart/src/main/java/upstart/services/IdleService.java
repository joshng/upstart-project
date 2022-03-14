package upstart.services;

import com.google.common.util.concurrent.AbstractService;
import com.google.common.util.concurrent.MoreExecutors;
import upstart.util.concurrent.AtomicMutableReference;
import upstart.util.exceptions.ThrowingRunnable;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;

public abstract class IdleService extends BaseComposableService<IdleService.DelegateService> {
  private static final int MAX_RECORDED_FAILURES = 10;
  private final AtomicMutableReference<Throwable> pendingFailure = new AtomicMutableReference<>();
  private final Set<Throwable> failures = ConcurrentHashMap.newKeySet(MAX_RECORDED_FAILURES);

  protected IdleService() {
    super(new DelegateService());
    delegate().wrapper = this;
  }

  /**
   * Start the service.
   */
  protected abstract void startUp() throws Exception;

  /**
   * Stop the service.
   */
  protected abstract void shutDown() throws Exception;

  /**
   * Notify that the service has failed. Ensures that {@link #shutDown()} will be invoked, and
   * transitions the service to the {@link State#FAILED FAILED} state.
   */
  protected void notifyFailed(Throwable cause) {
    if (failures.size() >= MAX_RECORDED_FAILURES || !failures.add(cause)) return;
    while (true) {
      Throwable prev = pendingFailure.get();
      if (prev != null) {
        if (prev != cause) prev.addSuppressed(cause);
        break;
      } else if (pendingFailure.compareAndSet(null, cause)) {
        break;
      }
    }
    stop();
  }

  protected boolean startUpOnSeparateThread() {
    return true;
  }

  protected boolean shutDownOnSeparateThread() {
    return true;
  }

  static class DelegateService extends AbstractService {
    private IdleService wrapper;

    @Override
    protected final void doStart() {
      stateTransition(State.STARTING, () -> {
        notifyFailure(wrapper::startUp);
        notifyStarted(); // if startup failed, this will transition directly to STOPPING
      });
    }

    @Override
    protected final void doStop() {
      stateTransition(State.STOPPING, () -> {
        notifyFailure(wrapper::shutDown);
        wrapper.pendingFailure.getOptional()
                .ifPresentOrElse(
                        this::notifyFailed,
                        this::notifyStopped
                );
      });
    }

    private void notifyFailure(ThrowingRunnable transition) {
      try {
        transition.runOrThrow();
      } catch (Throwable e) {
        wrapper.notifyFailed(e);
      }
    }

    private void stateTransition(State state, Runnable transition) {
      executor(state).execute(() -> {
        Thread currentThread = Thread.currentThread();
        String prevName = currentThread.getName();
        try {
          currentThread.setName(wrapper.serviceStateString(state));
          transition.run();
        } finally {
          currentThread.setName(prevName);
        }
      });
    }

    private Executor executor(State state) {
      if ((state == State.STARTING && wrapper.startUpOnSeparateThread()) || wrapper.shutDownOnSeparateThread()) {
        return task -> new Thread(task).start();
      } else {
        return MoreExecutors.directExecutor();
      }
    }

    @Override
    public String toString() {
      return wrapper.toString();
    }
  }
}
