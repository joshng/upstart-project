package upstart.services;

import com.google.common.util.concurrent.AbstractScheduledService;
import com.google.common.util.concurrent.Service;
import com.google.inject.Binder;
import com.google.inject.TypeLiteral;
import com.google.inject.binder.LinkedBindingBuilder;
import com.google.inject.multibindings.MapBinder;

import javax.inject.Inject;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.UnaryOperator;

public abstract class ScheduledService extends BaseComposableService<ScheduledService.DelegateService> {
  @Inject private Map<Class<? extends ScheduledService>, UnaryOperator<ScheduledExecutorService>> executorServiceMap;

  public static LinkedBindingBuilder<UnaryOperator<ScheduledExecutorService>> overrideExecutorService(Binder binder, Class<? extends ScheduledService> scheduledServiceClass) {
    return executorServiceBinder(binder).addBinding(scheduledServiceClass);
  }

  public static MapBinder<Class<? extends ScheduledService>, UnaryOperator<ScheduledExecutorService>> executorServiceBinder(Binder binder) {
    return MapBinder.newMapBinder(binder, new TypeLiteral<>() {}, new TypeLiteral<>(){});
  }

  protected ScheduledService() {
    super(new DelegateService());
    delegate().wrapper = this;
  }

  /**
   * Run one iteration of the scheduled task. If any invocation of this method throws an exception,
   * the service will transition to the {@link Service.State#FAILED} state and this method will no
   * longer be called.
   */
  protected abstract void runOneIteration() throws Exception;

  /**
   * Start the service.
   *
   * <p>By default this method does nothing.
   */
  protected void startUp() throws Exception {}

  /**
   * Stop the service. This is guaranteed not to run concurrently with {@link #runOneIteration}.
   *
   * <p>By default this method does nothing.
   * @see AbstractScheduledService#shutDown
   */
  protected void shutDown() throws Exception {}

  /**
   * Returns the {@link Schedule} object used to configure this service. This method will only be
   * called once.
   */
  protected abstract Schedule schedule();

  protected static Schedule fixedRateSchedule(Duration initialDelay, Duration period) {
    return Schedule.fixedRate(initialDelay, period);
  }

  protected static Schedule fixedDelaySchedule(Duration initialDelay, Duration period) {
    return Schedule.fixedDelay(initialDelay, period);
  }

  /**
   * Returns the {@link ScheduledExecutorService} that will be used to execute the {@link #startUp},
   * {@link #runOneIteration} and {@link #shutDown} methods. If this method is overridden the
   * executor will not be {@linkplain ScheduledExecutorService#shutdown shutdown} when this service
   * {@linkplain Service.State#TERMINATED terminates} or {@linkplain Service.State#TERMINATED
   * fails}. Subclasses may override this method to supply a custom {@link ScheduledExecutorService}
   * instance. This method is guaranteed to only be called once.
   *
   * <p>By default this returns a new {@link ScheduledExecutorService} with a single thread thread
   * pool that sets the name of the thread to the {@linkplain #serviceName() service name}. Also,
   * the pool will be {@linkplain ScheduledExecutorService#shutdown() shut down} when the service
   * {@linkplain Service.State#TERMINATED terminates} or {@linkplain Service.State#TERMINATED
   * fails}.
   */
  protected ScheduledExecutorService executor() {
    return delegate().defaultExecutor();
  }


  public static class Schedule {
    final AbstractScheduledService.Scheduler scheduler;

    /**
     * Returns a {@link Schedule} that runs the task using the {@link
     * ScheduledExecutorService#scheduleAtFixedRate} method.
     *
     * @param initialDelay the time to delay first execution
     * @param period the period between successive executions of the task
     */
    public static Schedule fixedRate(Duration initialDelay, Duration period) {
      return new Schedule(AbstractScheduledService.Scheduler.newFixedRateSchedule(initialDelay.toNanos(), period.toNanos(), TimeUnit.NANOSECONDS));
    }

    /**
     * Returns a {@link Schedule} that runs the task using the {@link
     * ScheduledExecutorService#scheduleWithFixedDelay} method.
     *
     * @param initialDelay the time to delay first execution
     * @param delay the delay between the termination of one execution and the commencement of the
     *     next
     */
    public static Schedule fixedDelay(Duration initialDelay, Duration delay) {
      return new Schedule(AbstractScheduledService.Scheduler.newFixedDelaySchedule(initialDelay.toNanos(), delay.toNanos(), TimeUnit.NANOSECONDS));
    }

    public Schedule(AbstractScheduledService.Scheduler scheduler) {
      this.scheduler = scheduler;
    }
  }

  static class DelegateService extends AbstractScheduledService {
    private ScheduledService wrapper;

    @Override
    protected void startUp() throws Exception {
      wrapper.startUp();
    }

    @Override
    protected void shutDown() throws Exception {
      wrapper.shutDown();
    }

    @Override
    protected String serviceName() {
      return wrapper.serviceStateString(state());
    }

    @Override
    protected void runOneIteration() throws Exception {
      wrapper.runOneIteration();
    }

    @Override
    protected ScheduledExecutorService executor() {
      return Optional.ofNullable(wrapper.executorServiceMap.get(wrapper.unenhancedClass))
              .orElse(UnaryOperator.identity())
              .apply(wrapper.executor());
    }

    private ScheduledExecutorService defaultExecutor() {
      return super.executor();
    }

    @Override
    protected Scheduler scheduler() {
      return wrapper.schedule().scheduler;
    }

  }
}
