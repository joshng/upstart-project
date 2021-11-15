package upstart.b4.devops;

import upstart.services.IdleService;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;

import javax.inject.Singleton;

@Singleton
class Fabric8ClientService extends IdleService {
  private KubernetesClient client;


  @Override
  protected void startUp() throws Exception {
    client = new DefaultKubernetesClient();
  }

  KubernetesClient getClient() {
    return client;
  }

  @Override
  protected void shutDown() throws Exception {
    client.close();
    client = null;
  }
}
