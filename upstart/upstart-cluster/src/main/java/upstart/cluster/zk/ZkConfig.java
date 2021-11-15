package upstart.cluster.zk;

import upstart.config.annotations.ConfigPath;
import org.apache.curator.x.async.WatchMode;
import org.immutables.value.Value;

import java.time.Duration;

@Value.Style(defaultAsDefault = true)
@ConfigPath("upstart.cluster.zk")
public interface ZkConfig {
    static ImmutableZkConfig.Builder builder() {
      return ImmutableZkConfig.builder();
    }

    String connectionString();

    default Duration sessionTimeout() {
      return Duration.ofSeconds(60);
    }

    default Duration connectionTimeout() {
      return Duration.ofSeconds(15);
    }

    default Duration retryBaseSleep() {
      return Duration.ofMillis(100);
    }

    default Duration retryMaxSleep() {
      return Duration.ofSeconds(2);
    }

    default int maxTryCount() {
      return 29;
    }

    /**
     * If the connection to zk is temporarily lost, async-curator normally causes untriggered watch-futures to throw
     * {@link org.apache.curator.x.async.AsyncEventException}, which notifies watchers that triggers might be delayed.
     * This behavior is configurable via its {@link WatchMode} variants.<p/>
     *
     * We're usually not concerned about temporary connection-loss for watches; we just want to be notified if
     * the watch actually fires, so we specify {@link WatchMode#successOnly} as our default.<p/>
     *
     * (If our zk <strong>session</strong> is eventually lost, then the {@link CuratorService}
     * itself will initiate shutdown.)
     */
    default WatchMode defaultAsyncWatchMode() {
        return WatchMode.successOnly;
    }
}
