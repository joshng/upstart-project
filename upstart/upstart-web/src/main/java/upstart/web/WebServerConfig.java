package upstart.web;

import upstart.config.annotations.ConfigPath;
import upstart.util.collect.PairStream;

import java.util.List;
import java.util.Map;
import java.util.Set;

@ConfigPath("upstart.web.server")
public interface WebServerConfig {
  String host();

  int port();

  String contextPath();

  boolean allowCorsForAllOrigins();

  List<String> corsAllowedOrigins();

  default String[] corsAllowedOriginsArray() {
    return corsAllowedOrigins().toArray(String[]::new);
  }
}
