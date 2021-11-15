package upstart.metrics;

import com.codahale.metrics.InstrumentedScheduledExecutorService;
import com.codahale.metrics.MetricRegistry;
import upstart.ExecutorServiceScheduler;
import upstart.config.UpstartModule;

import javax.inject.Inject;
import java.util.concurrent.ScheduledExecutorService;

public class InstrumentedSystemSchedulerModule extends UpstartModule {
  @Override
  protected void configure() {
    install(ExecutorServiceScheduler.Module.class);
    ExecutorServiceScheduler.Module.bindExecutorService(binder()).toProvider(InstrumentedSchedulerProvider.class);
  }

  static class InstrumentedSchedulerProvider extends ExecutorServiceScheduler.Module.ScheduledExecutorServiceProvider {
    private final MetricRegistry metricRegistry;

    @Inject
    InstrumentedSchedulerProvider(MetricRegistry metricRegistry) {
      this.metricRegistry = metricRegistry;
    }

    @Override
    public ScheduledExecutorService get() {
      // TODO: metric-name could be configurable
      return new InstrumentedScheduledExecutorService(super.get(), metricRegistry, "upstart.UpstartScheduler");
    }
  }
}
