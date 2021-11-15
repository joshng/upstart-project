package upstart.b4.devops;

import upstart.config.UpstartModule;
import io.fabric8.kubernetes.client.KubernetesClient;

public class Fabric8Module extends UpstartModule {
  @Override
  protected void configure() {
    bindDynamicProxy(KubernetesClient.class).initializedFrom(Fabric8ClientService.class, Fabric8ClientService::getClient);
    serviceManager().manage(Fabric8ClientService.class);
  }
}
