package upstart.provisioning;

import com.google.inject.Binder;
import com.google.inject.Key;
import com.google.inject.multibindings.Multibinder;
import upstart.config.UpstartModule;
import upstart.config.annotations.ConfigPath;
import upstart.managedservices.ServiceLifecycle;
import upstart.util.concurrent.Promise;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Singleton
public class ProvisioningService {
  private final Set<ProvisionedResource> resources = ConcurrentHashMap.newKeySet();
  private final Set<ProvisionedResource.ResourceType> resourcesToProvision;

  @Inject
  public ProvisioningService(Set<ProvisionedResource.ResourceType> resourcesToProvision) {
    this.resourcesToProvision = resourcesToProvision;
  }

  public static void bindProvisionedResource(Binder binder, Class<? extends ProvisionedResource> resource) {
    bindProvisionedResource(binder, Key.get(resource));
  }

  public static void bindProvisionedResource(Binder binder, Key<? extends ProvisionedResource> resource) {
    binder.install(new UpstartModule(resource) {
      @Override
      protected void configure() {
        install(new ProvisioningModule());
        serviceManager().manage(resource, ServiceLifecycle.Phase.Infrastructure);
      }
    });
  }

  public static Multibinder<ProvisionedResource.ResourceType> provisionedResourceTypeBinder(Binder binder) {
    return Multibinder.newSetBinder(binder, ProvisionedResource.ResourceType.class);
  }

  public static void provisionAtStartup(Binder binder, ProvisionedResource.ResourceType... resourceTypes) {
    Multibinder<ProvisionedResource.ResourceType> resourceBinder = provisionedResourceTypeBinder(binder);
    for (ProvisionedResource.ResourceType resourceType : resourceTypes) {
      resourceBinder.addBinding().toInstance(resourceType);
    }
  }

  public void addResource(ProvisionedResource resource) {
    resources.add(resource);
  }

  public Set<ProvisionedResource> getResources() {
    return resources;
  }

  public Promise<Void> ensureProvisioned(ProvisionedResource resource) {
    return resourcesToProvision.contains(resource.resourceType())
            ? resource.provisionIfNotExists().thenReplaceFuture(resource::waitUntilProvisioned)
            : resource.waitUntilProvisioned();
  }

  public static class ProvisioningModule extends UpstartModule {
    @Override
    protected void configure() {
      provisionedResourceTypeBinder(binder());
    }
  }

  @ConfigPath("upstart.provisioning")
  public interface ProvisioningConfig {
    boolean provisionAtStartup();
  }
}
