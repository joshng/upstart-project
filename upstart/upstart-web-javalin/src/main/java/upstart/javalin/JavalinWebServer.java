package upstart.javalin;

import upstart.util.concurrent.services.IdleService;
import upstart.web.WebServerConfig;
import io.javalin.Javalin;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Set;

@Singleton
public class JavalinWebServer extends IdleService {
  private final WebServerConfig serverConfig;
  private final Set<JavalinWebInitializer> plugins;
  private Javalin javalin;

  @Inject
  public JavalinWebServer(WebServerConfig serverConfig, Set<JavalinWebInitializer> plugins) {
    this.serverConfig = serverConfig;
    this.plugins = plugins;
  }

  @Override
  protected void startUp() throws Exception {
    javalin = Javalin.create(
            config -> {
              config.contextPath = serverConfig.contextPath();
              plugins.forEach(plugin -> plugin.initializeWeb(config));
            }
    ).start(serverConfig.host(), serverConfig.port());
  }

  @Override
  protected void shutDown() throws Exception {
    if (javalin != null) javalin.stop();
  }
}
