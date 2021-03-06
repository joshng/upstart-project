package upstart.log;

import upstart.UpstartStaticInitializer;
import upstart.config.UpstartEnvironment;
import upstart.util.collect.Optionals;
import org.kohsuke.MetaInfServices;

@MetaInfServices(UpstartStaticInitializer.class)
public class UpstartLogStaticInitializer extends UpstartStaticInitializer {
  public void initialize() {
    UpstartLogProvider.CLASSPATH_PROVIDER.ifPresentOrElse(
            classpathProvider -> UpstartEnvironment.loadAmbientConfigValue(UpstartLogConfig.class).apply(),
            () -> System.err.println("WARNING: no UpstartLogProvider found on the classpath. "
                    + "This means upstart.log configuration settings will not be applied! "
                    + "(to avoid this warning, include upstart-log4j or another META-INF/services implementation on the classpath)"
            )
    );
  }
}
