package upstart.log4j;

import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.kohsuke.MetaInfServices;

@MetaInfServices(Module.class)
public class Log4jConfigDeserializerModule extends SimpleModule {
  public Log4jConfigDeserializerModule() {
    registerSubtypes(
            ConsoleLog4jAppenderConfig.JsonLayoutConfig.class,
            ConsoleLog4jAppenderConfig.PatternLayoutConfig.class,
            ConsoleLog4jAppenderConfig.class
    );
  }
}
