package upstart.javalin;

import io.javalin.http.Handler;
import io.javalin.plugin.openapi.dsl.OpenApiBuilder;
import io.javalin.plugin.openapi.dsl.OpenApiDocumentation;
import upstart.config.UpstartApplicationConfig;
import upstart.config.UpstartModule;
import upstart.web.ConfigEndpointConfig;
import com.typesafe.config.ConfigObject;
import com.typesafe.config.ConfigRenderOptions;
import io.javalin.core.JavalinConfig;

import javax.inject.Inject;
import java.nio.charset.StandardCharsets;

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

public class RenderedConfigEndpoint implements JavalinWebInitializer {
  private static final OpenApiDocumentation OPEN_API_IGNORE = OpenApiBuilder.document().ignore();
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
  public void initializeWeb(JavalinConfig config) {
    byte[] rendered = appConfig.activeConfig().root().render(endpointConfig.renderOptions().parsed()).getBytes(StandardCharsets.UTF_8);
    config.registerPlugin(javalin -> javalin.get(endpointConfig.uri(), OpenApiBuilder.documented(OPEN_API_IGNORE, ctx -> {
      if (endpointConfig.renderOptions().json()) ctx.contentType("application/json");
      ctx.result(rendered);
    }), AdminRole.Instance));
  }

  public static class Module extends UpstartModule implements JavalinWebModule {
    @Override
    protected void configure() {
      bindConfig(ConfigEndpointConfig.class);
      addJavalinWebBinding().to(RenderedConfigEndpoint.class);
    }
  }
}
