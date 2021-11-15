package upstart.javalin;

import com.google.inject.Binder;
import com.google.inject.binder.LinkedBindingBuilder;
import com.google.inject.multibindings.Multibinder;
import upstart.UpstartModuleExtension;

public interface JavalinWebModule extends UpstartModuleExtension {
  static Multibinder<JavalinWebInitializer> javalinWebBinder(Binder binder) {
    return InternalJavalinWebServerModule.webBinder(binder);
  }

  default Multibinder<JavalinWebInitializer> javalinWebBinder() {
    return javalinWebBinder(binder());
  }

  default LinkedBindingBuilder<JavalinWebInitializer> addJavalinWebBinding() {
    return javalinWebBinder().addBinding();
  }
}
