package upstart.javalin;

import org.eclipse.jetty.util.log.Slf4jLog;
import org.kohsuke.MetaInfServices;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import upstart.UpstartStaticInitializer;

@MetaInfServices(UpstartStaticInitializer.class)
public class JettySlf4jStaticInitializer extends UpstartStaticInitializer {
  private static final Logger LOG = LoggerFactory.getLogger(InternalJavalinWebServerModule.class);
  private static final String JETTY_LOG_CLASS = "org.eclipse.jetty.util.log.class";


  static {
    configureJettySlf4jLogs();
  }

  public static void configureJettySlf4jLogs() {
    String property = System.getProperty(JETTY_LOG_CLASS);
    if (property == null) {
      System.setProperty(JETTY_LOG_CLASS, Slf4jLog.class.getName());
      try {
        org.eclipse.jetty.util.log.Log.setLog(new Slf4jLog());
      } catch (Exception e) {
        LOG.warn("Unable to route jetty logs to slf4j", e);
      }
    }
  }
}
