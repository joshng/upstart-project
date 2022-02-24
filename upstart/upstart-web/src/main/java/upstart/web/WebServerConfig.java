package upstart.web;

import upstart.config.annotations.ConfigPath;

import java.nio.file.Path;

@ConfigPath("upstart.web.server")
public interface WebServerConfig {
  String host();

  int port();

  String contextPath();
}
