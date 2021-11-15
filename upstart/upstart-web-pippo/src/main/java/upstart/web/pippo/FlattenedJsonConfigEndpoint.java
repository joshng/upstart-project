package upstart.web.pippo;

import com.fasterxml.jackson.databind.ObjectMapper;
import upstart.config.UpstartApplicationConfig;
import upstart.config.UpstartModule;
import upstart.util.exceptions.UncheckedIO;
import upstart.web.FlattenedJsonConfigEndpointConfig;
import ro.pippo.core.Application;

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
public class FlattenedJsonConfigEndpoint implements PippoWebInitializer {
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
  public void initializeWeb(Application app) {
    Map<String, String> configMap = appConfig.flattenedConfigProperties();
    String response = UncheckedIO.getUnchecked(() -> objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(configMap));

    app.GET(endpointConfig.uri(), routeContext -> {
      routeContext.json().send(response);
    });
  }

  public static class Module extends UpstartModule implements PippoWebModule {
    @Override
    protected void configure() {
      bindConfig(FlattenedJsonConfigEndpointConfig.class);
      addPippoWebBinding().to(FlattenedJsonConfigEndpoint.class);
    }
  }

}
