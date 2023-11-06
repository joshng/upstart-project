package upstart.provisioning;

import com.fasterxml.jackson.annotation.JsonValue;
import com.google.common.util.concurrent.Service;
import com.google.inject.AbstractModule;
import com.google.inject.Binder;
import com.google.inject.Key;
import com.google.inject.multibindings.Multibinder;
import upstart.config.UpstartModule;
import upstart.managedservices.ServiceLifecycle;
import upstart.util.concurrent.Promise;

public interface ProvisionedResource extends Service {
  static void bindProvisionedResource(Binder binder, Class<? extends ProvisionedResource> resource) {
    bindProvisionedResource(binder, Key.get(resource));
  }

  static void bindProvisionedResource(Binder binder, Key<? extends ProvisionedResource> resource) {
    binder.install(new UpstartModule(resource) {
      @Override
      protected void configure() {
        install(new ResourceProvisioningCoordinator.ProvisioningModule());
        serviceManager().manage(resource, ServiceLifecycle.Phase.Infrastructure);
      }
    });
  }

  static Multibinder<ResourceType> provisionedResourceTypeBinder(Binder binder) {
    return Multibinder.newSetBinder(binder, ResourceType.class);
  }

  static void provisionAtStartup(Binder binder, ResourceType resourceType, ResourceType... moreTypes) {
    Multibinder<ResourceType> resourceBinder = provisionedResourceTypeBinder(binder);
    resourceBinder.addBinding().toInstance(resourceType);
    for (ResourceType type : moreTypes) {
      resourceBinder.addBinding().toInstance(type);
    }
  }

  String resourceId();

  String ownerEnvironment();

  BaseProvisionedResource.ResourceType resourceType();

  Object resourceConfig();

  Promise<Void> waitUntilProvisioned();

  Promise<Void> provisionIfNotExists();


  default ResourceRequirement resourceRequirement() {
    return new ResourceRequirement(resourceId(), resourceType(), resourceConfig());
  }

  record ResourceType(String resourceType) {
    @JsonValue
    public String getResourceType() {
      return resourceType;
    }

    public AbstractModule startupProvisioningModule() {
      return new StartupProvisioningModule(this);
    }
  }

  record ResourceRequirement(String resourceId, ResourceType resourceType, Object resourceConfig) {
    public ResourceRequirement {
      if (resourceId == null) {
        throw new IllegalArgumentException("resourceId cannot be null");
      }
      if (resourceType == null) {
        throw new IllegalArgumentException("resourceType cannot be null");
      }
    }
  }

  class StartupProvisioningModule extends UpstartModule {
    private final ResourceType resourceType;

    public StartupProvisioningModule(ResourceType resourceType) {
      super(ProvisionedResource.class);
      this.resourceType = resourceType;
    }

    @Override
    protected void configure() {
      provisionAtStartup(binder(), resourceType);
    }
  }
}
