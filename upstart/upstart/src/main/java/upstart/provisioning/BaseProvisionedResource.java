package upstart.provisioning;

import upstart.util.concurrent.CompletableFutures;
import upstart.util.concurrent.services.AsyncService;

import javax.inject.Inject;
import java.util.concurrent.CompletableFuture;

public abstract class BaseProvisionedResource extends AsyncService implements ProvisionedResource {
  private ResourceProvisioningCoordinator provisioningService;

  @Inject
  void registerProvisionedResource(ResourceProvisioningCoordinator provisioningCoordinator) {
    this.provisioningService = provisioningCoordinator;
    provisioningCoordinator.addResource(this);
  }

  @Override
  protected CompletableFuture<?> startUp() throws Exception {
    return provisioningService.ensureProvisioned(this);
  }

  @Override
  protected CompletableFuture<?> shutDown() throws Exception {
    return CompletableFutures.nullFuture();
  }

  @Override
  public String serviceName() {
    return super.serviceName() + '{' + resourceId() + '}';
  }
}
