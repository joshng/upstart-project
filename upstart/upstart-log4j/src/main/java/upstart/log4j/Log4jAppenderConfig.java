package upstart.log4j;

import upstart.log.UpstartLogConfig;
import org.apache.log4j.Appender;

public interface Log4jAppenderConfig extends UpstartLogConfig.AppenderConfig {
  Appender configureAppender(String name, UpstartLogConfig config);
}
