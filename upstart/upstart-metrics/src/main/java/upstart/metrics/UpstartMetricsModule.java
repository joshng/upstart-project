package upstart.metrics;

import com.codahale.metrics.MetricRegistry;
import com.google.inject.Binder;
import com.google.inject.Module;
import com.google.inject.multibindings.Multibinder;
import upstart.config.UpstartModule;
import org.kohsuke.MetaInfServices;

@MetaInfServices(Module.class)
public class UpstartMetricsModule extends UpstartModule {
  public static Multibinder<TaggedMetricReporter> reporterBinder(Binder binder) {
    return Multibinder.newSetBinder(binder, TaggedMetricReporter.class);
  }

  @Override
  protected void configure() {
    UpstartMetricsModule.reporterBinder(binder()); // initialize multibinder in case no real reporters are registered
    bind(MetricRegistry.class).to(TaggedMetricRegistry.class);
    bind(TaggedMetricRegistry.class).asEagerSingleton();
  }
}
