package upstart.services;

import com.google.common.util.concurrent.AbstractExecutionThreadService;

/**
 * Base class for services that can implement {@link #startUp}, {@link #run} and {@link #shutDown}
 * methods. This class uses a single thread to execute the service; consider {@link NotifyingService}
 * if you would like to manage any threading manually, or {@link IdleService} if you just need threads for
 * startup/shutdown.
 */
public abstract class ExecutionThreadService extends BaseComposableService<ExecutionThreadService.InnerExecutionThreadService> {

  protected ExecutionThreadService() {
    super(new InnerExecutionThreadService());
    delegate().wrapper = this;
  }

  /**
   * Start the service. This method is invoked on the execution thread.
   * <p>
   * <p>By default this method does nothing.
   */
  protected void startUp() throws Exception {}

  /**
   * Run the service. This method is invoked on the execution thread.
   * Implementations must respond to stop requests. You could poll for lifecycle
   * changes in a work loop:
   * <pre>
   *   public void run() {
   *     while ({@link #isRunning()}) {
   *       // perform a unit of work
   *     }
   *   }
   * </pre>
   * ...or you could respond to stop requests by implementing {@link
   * #triggerShutdown()}, which should cause {@link #run()} to return.
   */
  protected abstract void run() throws Exception;

  /**
   * Invoked to request the service to stop.
   * <p>
   * <p>By default this method does nothing.
   */
  protected void triggerShutdown() {}

  /**
   * Stop the service. This method is invoked on the execution thread.
   * <p>
   * <p>By default this method does nothing.
   */
  protected void shutDown() throws Exception {}

  static class InnerExecutionThreadService extends AbstractExecutionThreadService {
    private ExecutionThreadService wrapper;

    @Override
    protected void run() throws Exception {
      wrapper.run();
    }

    @Override
    protected void startUp() throws Exception {
      wrapper.startUp();
    }

    @Override
    protected void triggerShutdown() {
      wrapper.triggerShutdown();
    }

    @Override
    protected void shutDown() throws Exception {
      wrapper.shutDown();
    }

    @Override
    protected String serviceName() {
      return wrapper.serviceName();
    }
  }
}

