package upstart.cluster.zk;

import upstart.cluster.ClusterMembership;
import upstart.config.UpstartModule;

public class ZkClusterModule extends UpstartModule {
  @Override
  public void configure() {
    install(new CuratorServiceModule());

    bind(ClusterMembership.class).to(ZkClusterMembership.class);

    serviceManager()
            .manage(ZkClusterMembership.class)
            .manage(ZkClusterWatcher.class)
            ;
  }
}
