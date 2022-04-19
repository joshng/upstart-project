package upstart.javalin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Binder;
import com.google.inject.Key;
import com.google.inject.binder.LinkedBindingBuilder;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.multibindings.OptionalBinder;
import upstart.config.UpstartModule;
import upstart.javalin.annotations.Web;
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
    objectMapperBinder(binder()).setDefault().toInstance(new ObjectMapper());
    bindConfig(WebServerConfig.class);
    serviceManager().manage(JavalinWebServer.class);
  }

  public static LinkedBindingBuilder<ObjectMapper> bindObjectMapper(Binder binder) {
    return objectMapperBinder(binder).setBinding();
  }

  private static OptionalBinder<ObjectMapper> objectMapperBinder(Binder binder) {
    return OptionalBinder.newOptionalBinder(binder, Key.get(ObjectMapper.class, Web.class));
  }
}
