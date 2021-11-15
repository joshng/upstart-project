package upstart.config;

import upstart.UpstartStaticInitializer;
import org.kohsuke.MetaInfServices;
import org.slf4j.bridge.SLF4JBridgeHandler;

@MetaInfServices(UpstartStaticInitializer.class)
public class UpstartLogging extends UpstartStaticInitializer {
  static {
    SLF4JBridgeHandler.removeHandlersForRootLogger();
    SLF4JBridgeHandler.install();
  }
}
