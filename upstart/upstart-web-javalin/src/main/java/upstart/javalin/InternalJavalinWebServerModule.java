package upstart.javalin;

import com.google.inject.Binder;
import com.google.inject.multibindings.Multibinder;
import upstart.config.UpstartModule;
import upstart.web.WebServerConfig;

class InternalJavalinWebServerModule extends UpstartModule {
  private static final InternalJavalinWebServerModule INSTANCE = new InternalJavalinWebServerModule();

  // TODO: support multiple web-servers (on different ports), with customizable ServiceLifecycle
  static Multibinder<JavalinWebInitializer> webBinder(Binder binder) {
    binder.install(INSTANCE);
    return Multibinder.newSetBinder(binder, JavalinWebInitializer.class);
  }

  @Override
  protected void configure() {
    bindConfig(WebServerConfig.class);
    serviceManager().manage(JavalinWebServer.class);
  }
}
