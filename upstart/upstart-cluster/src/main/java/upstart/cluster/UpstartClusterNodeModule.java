package upstart.cluster;

import upstart.config.annotations.ConfigPath;
import upstart.config.UpstartModule;

public class UpstartClusterNodeModule extends UpstartModule {
  @Override
  protected void configure() {
    bind(ClusterNodeId.class).toInstance(bindConfig(UpstartClusterNodeConfig.class).nodeId());
  }


  @ConfigPath("upstart.cluster.node")
  interface UpstartClusterNodeConfig {
    ClusterNodeId nodeId();
  }
}
