package upstart.javalin;

import com.google.inject.Binder;
import com.google.inject.Key;
import com.google.inject.TypeLiteral;
import com.google.inject.binder.LinkedBindingBuilder;
import com.google.inject.multibindings.Multibinder;
import upstart.UpstartModuleExtension;
import upstart.javalin.annotations.HttpRegistry;

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

  default void serveHttp(Class<?> endpointPojoType) {
    serveHttp(Key.get(endpointPojoType));
  }

  default void serveHttp(TypeLiteral<?> endpointPojoType) {
    serveHttp(Key.get(endpointPojoType));
  }

  default void serveHttp(Key<?> endpointPojoKey) {
    binder().install(HttpRegistry.INSTANCE.webEndpointModule(endpointPojoKey));
  }
}
