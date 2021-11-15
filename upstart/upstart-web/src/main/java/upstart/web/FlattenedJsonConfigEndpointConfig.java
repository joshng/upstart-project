package upstart.web;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import upstart.config.annotations.ConfigPath;
import org.immutables.value.Value;

@ConfigPath("upstart.flattenedJsonConfigEndpoint")
public interface FlattenedJsonConfigEndpointConfig {
  String uri();
}
