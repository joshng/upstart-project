package upstart.provisioning;

import com.fasterxml.jackson.annotation.JsonValue;
import com.google.common.util.concurrent.Service;
import upstart.util.concurrent.Promise;

public interface ProvisionedResource extends Service {
  String resourceId();

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
}
