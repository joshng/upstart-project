package upstart;

import com.google.common.util.concurrent.ListeningScheduledExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.inject.Binder;
import com.google.inject.BindingAnnotation;
import com.google.inject.Key;
import com.google.inject.binder.LinkedBindingBuilder;
import com.google.inject.multibindings.OptionalBinder;
import upstart.config.annotations.ConfigPath;
import upstart.config.UpstartModule;
import upstart.services.ServiceLifecycle;
import upstart.services.ThreadPoolService;
import upstart.util.concurrent.NamedThreadFactory;
import upstart.util.concurrent.Scheduler;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

@Singleton
@ServiceLifecycle(ServiceLifecycle.Phase.Infrastructure)
public class ExecutorServiceScheduler extends ThreadPoolService implements Scheduler {
  private final ListeningScheduledExecutorService executorService;
  private final Clock clock;

  @Inject
  public ExecutorServiceScheduler(
          SchedulerConfig config,
          @SchedulerExecutor ScheduledExecutorService executorService,
          Clock clock
  ) {
    super(config.shutdownGracePeriod());
    this.executorService = MoreExecutors.listeningDecorator(executorService);
    this.clock = clock;
  }

  @Override
  public ListeningScheduledExecutorService scheduledExecutorService() {
    return executorService;
  }

  @Override
  public Clock clock() {
    return clock;
  }

  @Override
  protected ExecutorService buildExecutorService() {
    return executorService;
  }

  public static class Module extends UpstartModule {

    public static LinkedBindingBuilder<ScheduledExecutorService> bindExecutorService(Binder binder) {
      return executorServiceOptionalBinder(binder).setBinding();
    }

    @Override
    protected void configure() {
      bindConfig(ExecutorServiceScheduler.SchedulerConfig.class);
      executorServiceOptionalBinder(binder()).setDefault().toProvider(ScheduledExecutorServiceProvider.class);
      bind(Scheduler.class).to(ExecutorServiceScheduler.class);
      serviceManager().manage(ExecutorServiceScheduler.class);
    }

    private static OptionalBinder<ScheduledExecutorService> executorServiceOptionalBinder(Binder binder) {
      return OptionalBinder.newOptionalBinder(binder, Key.get(ScheduledExecutorService.class, SchedulerExecutor.class));
    }

    public static class ScheduledExecutorServiceProvider implements Provider<ScheduledExecutorService> {
      @Override
      public ScheduledExecutorService get() {
        return Executors.newScheduledThreadPool(2, new NamedThreadFactory("sched"));
      }
    }
  }

  @Retention(RetentionPolicy.RUNTIME)
  @BindingAnnotation
  @interface SchedulerExecutor {}

  @ConfigPath("upstart.scheduler")
  public interface SchedulerConfig {
    Duration shutdownGracePeriod();
  }
}
