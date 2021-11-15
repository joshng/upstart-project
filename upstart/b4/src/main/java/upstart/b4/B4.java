package upstart.b4;

import io.upstartproject.hojack.HojackConfigMapper;
import upstart.b4.config.TargetRegistry;
import upstart.config.UpstartEnvironment;
import upstart.util.Ambiance;
import com.typesafe.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class B4 {
  public static final Logger WARN_LOG = LoggerFactory.getLogger("b4.warnings");
  public static final String DEFAULT_ENVIRONMENT = "DEV";

  public static TargetRegistry buildTargetRegistry(Config appConfig) {
    return new TargetRegistry(loadSupConfig(appConfig), HojackConfigMapper.buildDefaultObjectMapper());
  }

  public static Config loadSupConfig(Config appConfig) {
    if (!Ambiance.ambientValue(UpstartEnvironment.UPSTART_ENVIRONMENT).isPresent()) {
      System.setProperty(UpstartEnvironment.UPSTART_ENVIRONMENT, DEFAULT_ENVIRONMENT);
    }

    return appConfig.withFallback(UpstartEnvironment.ambientEnvironment().baseConfig());
  }
}
