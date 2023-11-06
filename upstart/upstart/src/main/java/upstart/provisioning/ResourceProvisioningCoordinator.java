package upstart.provisioning;

import upstart.config.UpstartModule;
import upstart.config.annotations.ConfigPath;
import upstart.util.concurrent.Promise;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Singleton
public class ResourceProvisioningCoordinator {
  private final Set<ProvisionedResource> resources = ConcurrentHashMap.newKeySet();
  private final Set<ProvisionedResource.ResourceType> resourcesTypesToProvision;

  @Inject
  public ResourceProvisioningCoordinator(Set<ProvisionedResource.ResourceType> resourcesTypesToProvision) {
    this.resourcesTypesToProvision = resourcesTypesToProvision;
  }

  public void addResource(ProvisionedResource resource) {
    resources.add(resource);
  }

  public Set<ProvisionedResource> getResources() {
    return resources;
  }

  public Promise<Void> ensureProvisioned(ProvisionedResource resource) {
    return resourcesTypesToProvision.contains(resource.resourceType())
            ? resource.provisionIfNotExists().thenReplaceFuture(resource::waitUntilProvisioned)
            : resource.waitUntilProvisioned();
  }

  public static class ProvisioningModule extends UpstartModule {
    @Override
    protected void configure() {
      ProvisionedResource.provisionedResourceTypeBinder(binder());
    }
  }

  @ConfigPath("upstart.provisioning")
  public interface ProvisioningConfig {
    boolean provisionAtStartup();
  }
}
