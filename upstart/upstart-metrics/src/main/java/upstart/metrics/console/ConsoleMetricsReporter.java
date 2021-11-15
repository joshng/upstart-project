package upstart.metrics.console;

import com.codahale.metrics.ConsoleReporter;
import com.codahale.metrics.ScheduledReporter;
import upstart.config.annotations.ConfigPath;
import upstart.services.IdleService;
import upstart.metrics.TaggedMetricRegistry;
import upstart.metrics.TaggedMetricReporter;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Singleton
public class ConsoleMetricsReporter extends IdleService implements TaggedMetricReporter {
  private final Duration reportInterval;
  private volatile ScheduledReporter reporter;

  @Inject
  public ConsoleMetricsReporter(ConsoleReporterConfig metricsConfig) {
    this.reportInterval = metricsConfig.reportInterval();
  }

  @Override
  public void initMetricRegistry(TaggedMetricRegistry registry) {
    reporter = ConsoleReporter.forRegistry(registry).build();
  }

  @Override
  protected void startUp() throws Exception {
    reporter.start(reportInterval.toMillis(), TimeUnit.MILLISECONDS);
  }

  @Override
  protected void shutDown() throws Exception {
    reporter.stop();
    reporter.report();//one last report, as close doesn't handle that
  }

  @Override
  public void close() throws IOException {
    stop();
  }

  @ConfigPath("upstart.metrics.console")
  public interface ConsoleReporterConfig {
    Duration reportInterval();
  }
}
