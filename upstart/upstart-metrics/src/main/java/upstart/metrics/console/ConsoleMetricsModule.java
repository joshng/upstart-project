package upstart.metrics.console;

import upstart.config.UpstartModule;
import upstart.metrics.UpstartMetricsModule;

public class ConsoleMetricsModule extends UpstartModule {
  @Override
  protected void configure() {
    bindConfig(ConsoleMetricsReporter.ConsoleReporterConfig.class);
    serviceManager().manage(ConsoleMetricsReporter.class);
    UpstartMetricsModule.reporterBinder(binder()).addBinding().to(ConsoleMetricsReporter.class);
  }
}
