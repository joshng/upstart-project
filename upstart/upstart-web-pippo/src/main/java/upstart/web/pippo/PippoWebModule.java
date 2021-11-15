package upstart.web.pippo;

import com.google.inject.binder.LinkedBindingBuilder;
import com.google.inject.multibindings.Multibinder;
import upstart.UpstartModuleExtension;

public interface PippoWebModule extends UpstartModuleExtension {
  default Multibinder<PippoWebInitializer> pippoWebBinder() {
    return PippoWebServerModule.webBinder(binder());
  }

  default LinkedBindingBuilder<PippoWebInitializer> addPippoWebBinding() {
    return pippoWebBinder().addBinding();
  }
}
