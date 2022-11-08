package upstart.javalin;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.plugin.json.JavalinJackson;
import upstart.UpstartDeploymentStage;
import upstart.javalin.annotations.Web;
import upstart.util.collect.PairStream;
import upstart.util.concurrent.services.IdleService;
import upstart.web.WebServerConfig;
import io.javalin.Javalin;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

@Singleton
public class JavalinWebServer extends IdleService {
  private final ObjectMapper objectMapper;
  private final WebServerConfig serverConfig;
  private final Set<JavalinWebInitializer> plugins;
  private final boolean allowCorsForAllOrigins;
  private Javalin javalin;

  @Inject
  public JavalinWebServer(
          @Web ObjectMapper objectMapper,
          WebServerConfig serverConfig,
          UpstartDeploymentStage deploymentStage,
          Set<JavalinWebInitializer> plugins
  ) {
    this.objectMapper = objectMapper;
    this.serverConfig = serverConfig;
    this.plugins = plugins;
    allowCorsForAllOrigins = serverConfig.allowCorsForAllOrigins() || deploymentStage.isDevelopmentMode();
  }

  @Override
  protected void startUp() throws Exception {
    javalin = Javalin.create(
            config -> {
              config.contextPath = serverConfig.contextPath();
              if (allowCorsForAllOrigins) {
                config.enableCorsForAllOrigins();
              } else {
                String[] corsAllowedOrigins = serverConfig.corsAllowedOriginsArray();
                if (corsAllowedOrigins.length > 0) config.enableCorsForOrigin(corsAllowedOrigins);
              }
              // TODO: should we pass the objectMapper to each plugin for initialization?
              plugins.forEach(plugin -> plugin.initializeWeb(config));
              config.jsonMapper(new JavalinJackson(objectMapper));
            }
    ).start(serverConfig.host(), serverConfig.port());
  }

  @Override
  protected void shutDown() throws Exception {
    if (javalin != null) javalin.stop();
  }
}
