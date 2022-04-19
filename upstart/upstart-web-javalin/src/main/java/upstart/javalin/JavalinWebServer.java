package upstart.javalin;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.plugin.json.JavalinJackson;
import upstart.javalin.annotations.Web;
import upstart.util.concurrent.services.IdleService;
import upstart.web.WebServerConfig;
import io.javalin.Javalin;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Set;

@Singleton
public class JavalinWebServer extends IdleService {
  private final ObjectMapper objectMapper;
  private final WebServerConfig serverConfig;
  private final Set<JavalinWebInitializer> plugins;
  private Javalin javalin;

  @Inject
  public JavalinWebServer(
          @Web ObjectMapper objectMapper,
          WebServerConfig serverConfig,
          Set<JavalinWebInitializer> plugins
  ) {
    this.objectMapper = objectMapper;
    this.serverConfig = serverConfig;
    this.plugins = plugins;
  }

  @Override
  protected void startUp() throws Exception {
    javalin = Javalin.create(
            config -> {
              config.contextPath = serverConfig.contextPath();
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
