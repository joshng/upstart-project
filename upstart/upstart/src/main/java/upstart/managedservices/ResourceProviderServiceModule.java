package upstart.managedservices;

import com.google.common.reflect.TypeToken;
import com.google.inject.Key;
import upstart.config.UpstartModule;

import java.lang.reflect.Type;

public class ResourceProviderServiceModule<T, S extends ResourceProviderService<T>> extends UpstartModule {
  public ResourceProviderServiceModule(Key<? extends S> serviceKey) {
    super(serviceKey);

    Class<T> providedType = (Class<T>) TypeToken.of(serviceKey.getTypeLiteral().getType()).resolveType(ResourceProviderService.class.getTypeParameters()[0]).getRawType();
    bindDynamicProxy(serviceKey.ofType(providedType)).initializedFrom(serviceKey, ResourceProviderService::getResource);
  }
}
