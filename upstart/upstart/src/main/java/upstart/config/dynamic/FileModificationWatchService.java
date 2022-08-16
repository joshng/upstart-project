package upstart.config.dynamic;

import com.google.common.util.concurrent.Service;
import org.immutables.value.Value;
import upstart.config.UpstartModule;
import upstart.config.annotations.ConfigPath;
import upstart.util.functions.AsyncFunction;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;

import static com.google.common.base.Preconditions.checkState;

public interface FileModificationWatchService extends Service {
  void watch(Path path, AsyncFunction<? super Path, Void> callback);

  void initialize(Runnable runnable);


  class WatchServiceModule extends UpstartModule {
    @Override
    protected void configure() {
      Class<? extends FileModificationWatchService> serviceClass = bindConfig(FileModificationWatchServiceConfig.class)
              .usePolling()
              ? PollingFileModificationWatchService.class
              : JdkFileModificationWatchService.class;
      bind(FileModificationWatchService.class).to(serviceClass);
      serviceManager().manage(serviceClass);
    }
  }

  @ConfigPath("upstart.file-modification-watch")
  interface FileModificationWatchServiceConfig {
    Duration shutdownPollInterval();

    boolean usePolling();

    Optional<Duration> pollInterval();

    @Value.Auxiliary
    @Value.Derived
    default long shutdownPollMillis() {
      return shutdownPollInterval().toMillis();
    }

    @Value.Check
    default void check() {
      checkState(!usePolling() || pollInterval().isPresent(),
                 "pollInterval must be present if usePolling is true");
    }
  }
}
