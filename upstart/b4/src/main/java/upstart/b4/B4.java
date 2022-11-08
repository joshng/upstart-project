package upstart.b4;

import io.upstartproject.hojack.HojackConfigMapper;
import upstart.b4.config.TargetRegistry;
import upstart.config.UpstartEnvironment;
import upstart.util.Ambiance;
import com.typesafe.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class B4 {
  public static final Logger WARN_LOG = LoggerFactory.getLogger("b4.status");

  public static TargetRegistry buildTargetRegistry(Config appConfig) {
    return new TargetRegistry(loadB4Config(appConfig), HojackConfigMapper.buildDefaultObjectMapper());
  }

  public static Config loadB4Config(Config appConfig) {
    if (Ambiance.ambientValue(UpstartEnvironment.UPSTART_ENVIRONMENT).isEmpty()) {
      System.setProperty(UpstartEnvironment.UPSTART_ENVIRONMENT, UpstartEnvironment.DEFAULT_DEV_ENVIRONMENT_NAME);
    }

    return appConfig.withFallback(UpstartEnvironment.ambientEnvironment().baseConfig());
  }
}
