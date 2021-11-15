package upstart.log;

import com.google.common.base.Strings;
import com.google.common.collect.MoreCollectors;
import com.google.common.collect.Streams;
import upstart.config.UpstartApplicationConfig;
import upstart.util.LogLevel;
import org.kohsuke.MetaInfServices;
import org.slf4j.Logger;

import java.util.Optional;
import java.util.ServiceLoader;

/**
 * An abstract SPI integration-point for configuring  log-subsystem-specific settings with the abstract
 * {@link UpstartApplicationConfig}-managed {@link UpstartLogConfig}.<p/>
 *
 * A single implementation of {@link UpstartLogProvider} should be present on the classpath at runtime, registered
 * via {@link MetaInfServices} or equivalent.<p/>
 *
 * Implementations are expected to support  applying generic configuration via {@link #applyLogConfig}, and
 * emitting structured log-records via {@link #logWithPayload}.
 */
public interface UpstartLogProvider {
  Optional<UpstartLogProvider> CLASSPATH_PROVIDER = Streams.stream(ServiceLoader.load(UpstartLogProvider.class))
          .collect(MoreCollectors.toOptional());

  void applyLogConfig(UpstartLogConfig config);

  default void logWithPayload(Logger logger, LogLevel level, Object structuredLogEvent, String message, Object... args) {
    if (level.isEnabled(logger)) {
      logWithPayload(logger, level, structuredLogEvent, Strings.lenientFormat(message, args));
    }
  }

  void logWithPayload(Logger logger, LogLevel level, Object structuredLogEvent, String message);
}
