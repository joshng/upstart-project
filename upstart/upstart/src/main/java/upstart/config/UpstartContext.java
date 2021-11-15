package upstart.config;

import upstart.UpstartDeploymentStage;
import upstart.config.annotations.ConfigPath;

/**
 * Not intended for runtime use; only defined to expose the active context in the {@link UpstartApplicationConfig}.
 */
@ConfigPath("upstart.context")
public interface UpstartContext {
  String application();
  String owner();
  String environment();
  UpstartDeploymentStage deploymentStage();
}
