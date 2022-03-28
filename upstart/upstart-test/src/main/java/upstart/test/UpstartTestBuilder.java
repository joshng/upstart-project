package upstart.test;

import com.google.common.util.concurrent.Service;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import upstart.UpstartApplicationBuilder;
import upstart.config.TestConfigBuilder;
import upstart.util.reflect.Reflect;
import upstart.util.exceptions.ThrowingConsumer;
import org.mockito.Mockito;

import java.util.function.Consumer;

public interface UpstartTestBuilder extends UpstartApplicationBuilder<UpstartTestBuilder>, TestConfigBuilder<UpstartTestBuilder> {
  String UPSTART_INSTALL_AUTO_MODULES_CONFIGPATH = "upstart.autoModules.enabled";

  default UpstartTestBuilder installModule(Class<? extends Module> moduleClass) {
    return installModule(Reflect.newInstance(moduleClass));
  }

  default UpstartTestBuilder disableAutoModules() {
    return enableAutoModules(false);
  }

  default UpstartTestBuilder enableAutoModules(boolean enable) {
    return overrideConfig(UPSTART_INSTALL_AUTO_MODULES_CONFIGPATH, enable);
  }

  default UpstartTestBuilder suppressAutoModules(Class<? extends Module> moduleClass) {
    return suppressAutoModules(moduleClass.getCanonicalName());
  }

  default UpstartTestBuilder suppressAutoModules(String moduleClassName) {
    return overrideConfig("upstart.autoModules.install.\"" + moduleClassName + "\"", false);
  }

  UpstartTestBuilder overrideBindings(Module overrides);

  default <T> T bindMockInstance(Class<T> mockedClass) {
    return bindMockInstance(Key.get(mockedClass), mockedClass);
  }

  default <T> T bindMockInstance(Key<? super T> mockedKey, Class<T> mockedClass) {
    T instance = Mockito.mock(mockedClass);
    overrideInstance(mockedKey, instance);
    return instance;
  }

  default <T> UpstartTestBuilder overrideInstance(Class<? super T> boundClass, T instance) {
    return overrideInstance(Key.get(boundClass), instance);
  }

  default <T> UpstartTestBuilder overrideInstance(Key<? super T> boundKey, T instance) {
    overrideBindings(binder -> binder.skipSources(UpstartTestBuilder.class).bind(boundKey).toInstance(instance));
    return this;
  }

  default UpstartTestBuilder disableServiceManagement(Class<? extends Service> serviceKey) {
    return disableServiceManagement(Key.get(serviceKey));
  }

  UpstartTestBuilder disableServiceManagement(Key<? extends Service> serviceKey);

  default UpstartTestBuilder apply(Consumer<UpstartTestBuilder> block) {
    block.accept(this);
    return this;
  }

  UpstartTestBuilder withInjector(ThrowingConsumer<? super Injector> callback);

}
