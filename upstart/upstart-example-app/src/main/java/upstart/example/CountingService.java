package upstart.example;

import upstart.config.annotations.ConfigPath;
import upstart.config.UpstartModule;
import upstart.util.concurrent.services.ScheduledService;
import org.immutables.value.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

@Singleton
public class CountingService extends ScheduledService {
  private static final Logger LOG = LoggerFactory.getLogger(CountingService.class);
  private final CountingConfig config;
  private final AtomicInteger counter = new AtomicInteger();

  @Inject
  public CountingService(CountingConfig config) {
    this.config = config;
  }

  @Override
  protected Schedule schedule() {
    return fixedRateSchedule(Duration.ZERO, config.updatePeriod());
  }

  @Override
  protected void startUp() throws Exception {
    counter.set(config.initialValue());
  }

  @Override
  protected void runOneIteration() throws Exception {
    int count = counter.incrementAndGet();
    LOG.info("Counted {}", count);
  }

  public int getCount() {
    checkState(isRunning(), "%s was not running!", getClass().getSimpleName());
    return counter.get();
  }

  public static class CountingModule extends UpstartModule {
    @Override
    protected void configure() {
      bindConfig(CountingConfig.class);
      serviceManager().manage(CountingService.class);
    }
  }

  @ConfigPath("example-app.counting")
  public interface CountingConfig {
    static ImmutableCountingConfig.Builder builder() {
      return ImmutableCountingConfig.builder();
    }

    int initialValue();

    Duration updatePeriod();

    @Value.Check
    default void checkStuff() {
      checkArgument(initialValue() <= 3, "initialValue must not exceed 3", initialValue());
    }
  }
}
