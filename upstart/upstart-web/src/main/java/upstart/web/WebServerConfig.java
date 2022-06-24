package upstart.web;

import upstart.config.annotations.ConfigPath;
import upstart.util.collect.PairStream;

import java.util.Map;
import java.util.Set;

@ConfigPath("upstart.web.server")
public interface WebServerConfig {
  String host();

  int port();

  String contextPath();

  boolean allowCorsForAllOrigins();

  Map<String, Boolean> corsAllowedOrigins();

  default String[] corsAllowedOriginsArray() {
    return PairStream.of(corsAllowedOrigins())
            .filterValues(v -> v)
            .keys()
            .toArray(String[]::new);
  }
}
