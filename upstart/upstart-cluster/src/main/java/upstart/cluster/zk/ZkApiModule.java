package upstart.cluster.zk;

import upstart.cluster.ClusterAdministrator;
import upstart.config.UpstartModule;

public class ZkApiModule extends UpstartModule {
  @Override
  public void configure() {
    install(new CuratorServiceModule());
    bind(ClusterAdministrator.class).to(ZkClusterAdministrator.class);
  }
}
