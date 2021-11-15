package upstart.javalin;

import com.fasterxml.jackson.databind.ObjectMapper;
import upstart.config.UpstartApplicationConfig;
import upstart.config.UpstartModule;
import upstart.util.exceptions.UncheckedIO;
import upstart.web.FlattenedJsonConfigEndpointConfig;
import io.javalin.core.JavalinConfig;

import javax.inject.Inject;
import java.util.Map;

/**
 * Defines a web-endpoint that renders the contents of the active {@link UpstartApplicationConfig} as a
 * flattened JSON-dictionary of key/value (string/string) pairs.
 * <p/>
 * The path for exposing this endpoint must be configured in HOCON as upstart.flattenedJsonConfigEndpoint.uri
 *
 * @see Module
 */
public class FlattenedJsonConfigEndpoint implements JavalinWebInitializer {
  private final FlattenedJsonConfigEndpointConfig endpointConfig;
  private final UpstartApplicationConfig appConfig;
  private final ObjectMapper objectMapper;

  @Inject
  public FlattenedJsonConfigEndpoint(
          FlattenedJsonConfigEndpointConfig endpointConfig,
          UpstartApplicationConfig appConfig,
          ObjectMapper objectMapper
  ) {
    this.endpointConfig = endpointConfig;
    this.appConfig = appConfig;
    this.objectMapper = objectMapper;
  }

  @Override
  public void initializeWeb(JavalinConfig config) {
    Map<String, String> configMap = appConfig.flattenedConfigProperties();
    byte[] response = UncheckedIO.getUnchecked(() -> objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(configMap));
    config.registerPlugin(javalin -> javalin.get(endpointConfig.uri(), ctx -> ctx.result(response)));
  }

  public static class Module extends UpstartModule implements JavalinWebModule {
    @Override
    protected void configure() {
      bindConfig(FlattenedJsonConfigEndpointConfig.class);
      addJavalinWebBinding().to(FlattenedJsonConfigEndpoint.class);
    }
  }
}
