package upstart.web;

import upstart.config.annotations.ConfigPath;

@ConfigPath("upstart.web.server")
public interface WebServerConfig {
  String host();

  int port();

  String contextPath();
}
