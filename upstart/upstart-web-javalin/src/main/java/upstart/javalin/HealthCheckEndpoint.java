package upstart.javalin;

import io.javalin.core.JavalinConfig;
import upstart.config.annotations.ConfigPath;
import upstart.healthchecks.HealthChecker;

import javax.inject.Inject;

public class HealthCheckEndpoint implements JavalinWebInitializer {
  private final HealthChecker healthChecker;
  private final HealthCheckEndpointConfig endpointConfig;

  @Inject
  public HealthCheckEndpoint(HealthCheckEndpointConfig endpointConfig, HealthChecker healthChecker) {
    this.healthChecker = healthChecker;
    this.endpointConfig = endpointConfig;
  }

  @Override
  public void initializeWeb(JavalinConfig config) {
    config.registerPlugin(javalin -> javalin.get(
            endpointConfig.livenessCheckPath(),
            ctx -> ctx.future(healthChecker.healthyPromise())
    ));
  }

  public static class HealthCheckEndpointModule extends UpstartJavalinModule {
    @Override
    protected void configure() {
      HealthChecker.healthCheckMapBinder(binder());
      bindConfig(HealthCheckEndpointConfig.class);
      addJavalinWebBinding().to(HealthCheckEndpoint.class);
    }
  }

  @ConfigPath( "upstart.healthCheckEndpoints" )
  public interface HealthCheckEndpointConfig {
    String livenessCheckPath();
  }
}
