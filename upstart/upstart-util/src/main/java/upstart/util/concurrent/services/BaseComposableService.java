package upstart.util.concurrent.services;


import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.Service;
import upstart.util.reflect.Reflect;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;


public abstract class BaseComposableService<S extends Service> implements ComposableService {

  private final S delegate;
  protected final Class<? extends BaseComposableService> unenhancedClass = Reflect.getUnenhancedClass(getClass());
  private final String unenhancedClassName = unenhancedClass.getSimpleName();

  private boolean started = false;
  private boolean stopped = false;
  private final CompletableFuture<State> startedPromise;
  private final CompletableFuture<State> stoppedPromise;
  private volatile boolean startupCanceled = false;

  protected BaseComposableService(S delegate) {
    this.delegate = delegate;

    if (delegate instanceof ComposableService upstartDelegate) {
      startedPromise = upstartDelegate.getStartedFuture();
      stoppedPromise = upstartDelegate.getStoppedFuture();
    } else {
      startedPromise = new CompletableFuture<>();
      stoppedPromise = new CompletableFuture<>();
      delegate.addListener(new Listener() {
        @Override
        public void stopping(State from) {
          if (from == State.STARTING) {
            startupCanceled = true;
            onStartupCanceled();
          }
        }

        @Override
        public void running() {
          startedPromise.complete(state());
        }

        @Override
        public void terminated(State from) {
          startedPromise.complete(from);
          stoppedPromise.complete(state());
        }

        @Override
        public void failed(State from, Throwable failure) {
          startedPromise.completeExceptionally(failure);
          stoppedPromise.completeExceptionally(failure);
        }
      }, MoreExecutors.directExecutor());
    }
  }

  /**
   * This method is invoked if the service is {@link #stopAsync stopped} before it has finished starting up. By
   * default, the {@link Service} machinery will wait until startup completes ({@link State#RUNNING}) before initiating
   * shutdown. Override this method to forcibly complete startup ASAP to accelerate the shutdown process.
   */
  protected void onStartupCanceled() {
  }

  protected boolean wasStartupCanceled() {
    return startupCanceled;
  }

  @Override
  public CompletableFuture<State> getStartedFuture() {
    return startedPromise;
  }

  @Override
  public CompletableFuture<State> getStoppedFuture() {
    return stoppedPromise;
  }

  protected final S delegate() {
    return delegate;
  }

  public boolean isRunning() {
    return delegate.isRunning();
  }

  public void addListener(Service.Listener listener, Executor executor) {
    delegate.addListener(listener, executor);
  }

  public Throwable failureCause() {
    return delegate.failureCause();
  }

  @Override
  public CompletableFuture<Service.State> start() {
    boolean doStart;
    synchronized (this) {
      if (!started) {
        started = true;
        doStart = !stopped;
      } else {
        doStart = false;
      }
    }
    if (doStart) delegate.startAsync();
    return startedPromise;
  }

  @Override
  public CompletableFuture<Service.State> stop() {
    boolean doStop;
    synchronized (this) {
      doStop = !stopped;
      stopped = true;
    }
    if (doStop) delegate.stopAsync();
    return stoppedPromise;
  }

  public Service.State state() {
    return delegate.state();
  }

  @Override
  public void awaitRunning() {
    delegate.awaitRunning();
  }

  @Override
  public void awaitRunning(long timeout, TimeUnit unit) throws TimeoutException {
    delegate.awaitRunning(timeout, unit);
  }

  @Override
  public void awaitTerminated() {
    delegate.awaitTerminated();
  }

  @Override
  public void awaitTerminated(long timeout, TimeUnit unit) throws TimeoutException {
    delegate.awaitTerminated(timeout, unit);
  }

  @Override
  public Service startAsync() {
    start();
    return this;
  }

  @Override
  public Service stopAsync() {
    stop();
    return this;
  }

  @Override
  public String serviceName() {
    return unenhancedClassName;
  }

  @Override
  public String toString() {
    return serviceStateString(state());
  }

  protected String serviceStateString(State state) {
    return serviceName() + "[" + stateChar(state) + "]";
  }

  public static String stateChar(State state) {
    return switch (state) {
      case NEW -> " ";
      // these unicode symbols are nicer, but can cause problems for systems that don't support them
//      case STARTING: return "➚";
//      case RUNNING: return "√";
//      case STOPPING: return "➘";
//      case TERMINATED: return "×";
//      case FAILED: return "!";
      case STARTING -> "U";
      case RUNNING -> "R";
      case STOPPING -> "D";
      case TERMINATED -> "T";
      case FAILED -> "!";
    };
  }
}
