package upstart.b4.functions;

import upstart.config.annotations.ConfigPath;

@ConfigPath("upstart.maven")
public interface MavenConfig {
  String mvnExecutable();
}
