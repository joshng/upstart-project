package upstart.b4.functions;

import upstart.config.annotations.ConfigPath;

@ConfigPath("maven")
public interface MavenConfig {
  String mvnExecutable();
}
