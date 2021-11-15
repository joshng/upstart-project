package upstart.log4j;

import com.google.common.collect.Maps;
import upstart.log.UpstartLogConfig;
import upstart.log.UpstartLogProvider;
import upstart.util.LogLevel;
import org.apache.log4j.Appender;
import org.apache.log4j.Level;
import org.apache.log4j.LogManager;
import org.apache.log4j.MDC;
import org.kohsuke.MetaInfServices;
import org.slf4j.Logger;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.google.common.base.Preconditions.checkArgument;

@MetaInfServices(UpstartLogProvider.class)
public class UpstartLog4jProvider implements UpstartLogProvider {
  private static final Map<UpstartLogConfig.LogThreshold, Level> LOG_THRESHOLD_LEVELS = Maps.immutableEnumMap(
          Stream.of(UpstartLogConfig.LogThreshold.values()).collect(Collectors.toMap(
                  Function.identity(),
                  thresh -> Level.toLevel(thresh.toString()))
          )
  );
  static final String STRUCTURED_LOG_PAYLOAD = "STRUCTURED_LOG_PAYLOAD";

  public void logWithPayload(Logger logger, LogLevel level, Object structuredLogEvent, String message) {
    if (level.isEnabled(logger)) {
      MDC.put(STRUCTURED_LOG_PAYLOAD, structuredLogEvent);
      try {
        level.log(logger, message);
      } finally {
        MDC.remove(STRUCTURED_LOG_PAYLOAD);
      }
    }
  }

  @Override
  public void applyLogConfig(UpstartLogConfig config) {
    setThreshold(LogManager.getRootLogger(), config.rootLogger());
    config.levels().forEach(UpstartLog4jProvider::setThreshold);
    config.appenders().forEach((name, appenderConfig) -> configureAppender(name, appenderConfig, config));
  }

  private static void configureAppender(String name, UpstartLogConfig.AppenderConfig appenderConfig, UpstartLogConfig config) {
    checkArgument(appenderConfig instanceof Log4jAppenderConfig, "AppenderConfig type incompatible with %s, expected %s: %s", UpstartLog4jProvider.class.getName(), Log4jAppenderConfig.class.getName(), appenderConfig);
    Log4jAppenderConfig log4jConfig = (Log4jAppenderConfig) appenderConfig;
    Appender appender = log4jConfig.configureAppender(name, config);
    appender.setName(name);
    org.apache.log4j.Logger rootLogger = LogManager.getRootLogger();
    rootLogger.removeAppender(name);
    rootLogger.addAppender(appender);
  }

  private static void setThreshold(String name, UpstartLogConfig.LogThreshold threshold) {
    setThreshold(LogManager.getLogger(name), threshold);
  }

  private static void setThreshold(org.apache.log4j.Logger logger, UpstartLogConfig.LogThreshold threshold) {
    logger.setLevel(toLevel(threshold));
  }

  public static Level toLevel(UpstartLogConfig.LogThreshold threshold) {
    return LOG_THRESHOLD_LEVELS.get(threshold);
  }
}
