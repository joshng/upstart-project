package upstart.web.pippo;

import upstart.config.UpstartApplicationConfig;
import upstart.config.UpstartModule;
import upstart.web.ConfigEndpointConfig;
import com.typesafe.config.ConfigObject;
import com.typesafe.config.ConfigRenderOptions;
import ro.pippo.core.Application;

import javax.inject.Inject;

/**
 * Defines a web-endpoint that renders the contents of the active {@link UpstartApplicationConfig} as
 * rendered by {@link ConfigObject#render(ConfigRenderOptions)}. The specific formatting of the output
 * is configurable via {@link ConfigEndpointConfig#renderOptions}.
 * <p/>
 * The path for exposing this endpoint must be configured in HOCON as upstart.configEndpoint.uri
 *
 * @see RenderedConfigEndpoint.Module
 * @see ConfigRenderOptions
 */

public class RenderedConfigEndpoint implements PippoWebInitializer {
  private final ConfigEndpointConfig endpointConfig;
  private final UpstartApplicationConfig appConfig;

  @Inject
  public RenderedConfigEndpoint(
          ConfigEndpointConfig endpointConfig,
          UpstartApplicationConfig appConfig
  ) {
    this.endpointConfig = endpointConfig;
    this.appConfig = appConfig;
  }

  @Override
  public void initializeWeb(Application app) {
    String rendered = appConfig.activeConfig().root().render(endpointConfig.renderOptions().parsed());
    app.GET(endpointConfig.uri(), routeContext -> {
      if (endpointConfig.renderOptions().json()) {
        routeContext.json();
      } else {
        routeContext.text();
      }
      routeContext.send(rendered);
    });
  }

  public static class Module extends UpstartModule implements PippoWebModule {
    @Override
    protected void configure() {
      bindConfig(ConfigEndpointConfig.class);
      addPippoWebBinding().to(RenderedConfigEndpoint.class);
    }
  }
}
