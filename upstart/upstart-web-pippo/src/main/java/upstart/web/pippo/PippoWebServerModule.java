package upstart.web.pippo;

import com.google.inject.Binder;
import com.google.inject.Module;
import com.google.inject.Scopes;
import com.google.inject.multibindings.Multibinder;
import upstart.config.UpstartConfigBinder;
import upstart.services.ManagedServicesModule;
import upstart.web.WebServerConfig;
import ro.pippo.core.Pippo;

enum PippoWebServerModule implements Module {
  Instance;

  public static Multibinder<PippoWebInitializer> webBinder(Binder binder) {
    binder.install(Instance);
    return Multibinder.newSetBinder(binder, PippoWebInitializer.class);
  }

  @Override
  public void configure(Binder binder) {
    ManagedServicesModule.serviceManager(binder).manage(PippoWebServer.class);
    binder.bind(Pippo.class).in(Scopes.SINGLETON);
    UpstartConfigBinder.get().bindConfig(binder, WebServerConfig.class);
  }
}
