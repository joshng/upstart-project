package upstart.web;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import upstart.config.annotations.ConfigPath;
import com.typesafe.config.ConfigRenderOptions;
import org.immutables.value.Value;
import upstart.config.annotations.DeserializedImmutable;

@ConfigPath("upstart.configEndpoint")
public interface ConfigEndpointConfig {
  String uri();

  RenderOptions renderOptions();

  @DeserializedImmutable
  interface RenderOptions {
    boolean originComments();

    boolean comments();

    boolean formatted();

    boolean json();

    @Value.Lazy
    default ConfigRenderOptions parsed() {
      return ConfigRenderOptions.defaults()
              .setOriginComments(originComments())
              .setComments(comments())
              .setFormatted(formatted())
              .setJson(json());
    }
  }
}
