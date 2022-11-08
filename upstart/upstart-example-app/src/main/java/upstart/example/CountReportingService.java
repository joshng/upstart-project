package upstart.example;

import com.google.inject.Guice;
import upstart.UpstartService;
import upstart.config.annotations.ConfigPath;
import upstart.config.UpstartModule;
import upstart.util.concurrent.services.ExecutionThreadService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@Singleton
public class CountReportingService extends ExecutionThreadService {
  private static final Logger LOG = LoggerFactory.getLogger(CountReportingService.class);

  private final ReportingConfig config;
  private final CountingService countingService;
  private final CountDownLatch shutdownLatch = new CountDownLatch(1);
  private volatile int latestCount;

  @Inject
  public CountReportingService(ReportingConfig c, CountingService countingService) {
    config = c;
    this.countingService = countingService;
  }

  /** startUp: perform any initialization necessary to prepare to run */
  @Override
  protected void startUp() {

    LOG.info("{} starting up", getClass().getSimpleName());
  }

  @Override
  protected void run() throws Exception {
    while (isRunning()) {
      latestCount = countingService.getCount();
      LOG.info("Report: latest count is {}", latestCount);
      // sleep for our reportPeriod, but respond promptly to a shutdown-request
      // (note: "sleeping" is usually a poor practice in production code; prefer event- or timer-driven when feasible)
      shutdownLatch.await(config.reportPeriod().toMillis(), TimeUnit.MILLISECONDS);
    }
  }

  public int latestCount() {
    return latestCount;
  }

  /** triggerShutdown (optional): arrange a signal to cause the execution-thread to return promptly from run() */
  @Override
  protected void triggerShutdown() {
    shutdownLatch.countDown();
  }

  /** shutDown: perform any final cleanup after run() has returned */
  @Override
  protected void shutDown() {
    LOG.info("shutting down");
  }

  /**
   * A {@link Guice} module that configures this service to run with {@link UpstartService}
   */
  public static class ReportingModule extends UpstartModule {
    @Override
    protected void configure() {
      // ensure that dependencies are configured
      install(new CountingService.CountingModule());

      bindConfig(ReportingConfig.class);
      serviceManager().manage(CountReportingService.class);
    }
  }

  @ConfigPath("example-app.reporting")
  public interface ReportingConfig {
    Duration reportPeriod();
  }
}
