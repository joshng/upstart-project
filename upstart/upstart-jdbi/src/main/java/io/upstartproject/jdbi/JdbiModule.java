package io.upstartproject.jdbi;

import com.google.inject.Binder;
import com.google.inject.Key;
import com.google.inject.TypeLiteral;
import com.google.inject.binder.LinkedBindingBuilder;
import com.google.inject.multibindings.Multibinder;
import org.jdbi.v3.core.spi.JdbiPlugin;
import org.jdbi.v3.sqlobject.SqlObjectPlugin;
import upstart.config.UpstartModule;
import upstart.guice.AnnotationKeyedPrivateModule;
import upstart.proxy.DynamicProxyBindingBuilder;

import java.lang.annotation.Annotation;
import java.util.Set;

public class JdbiModule extends UpstartModule {
  private static final TypeLiteral<Set<JdbiPlugin>> PLUGIN_SET_TYPE = new TypeLiteral<>(){};
  private final Key<? extends JdbiService.JdbiInitializer> initializerKey;
  private final Key<? extends JdbiService> serviceKey;
  private final Annotation bindingAnnotation;

  public JdbiModule(
          Key<? extends JdbiService.JdbiInitializer> initializerKey,
          Annotation bindingAnnotation
  ) {
    super(initializerKey, bindingAnnotation);
    this.initializerKey = initializerKey;
    this.bindingAnnotation = bindingAnnotation;
    serviceKey = Key.get(JdbiService.class, bindingAnnotation);
  }

  @Override
  protected void configure() {
    install(new AnnotationKeyedPrivateModule(bindingAnnotation, JdbiService.class) {
      @Override
      protected void configurePrivateScope() {
        bindPrivateBinding(JdbiService.JdbiInitializer.class)
                .to(initializerKey)
                .asEagerSingleton();
        bindPrivateBindingToAnnotatedKey(PLUGIN_SET_TYPE);
      }
    });

    pluginBinder(binder());
    serviceManager().manage(Key.get(JdbiService.class, bindingAnnotation));
  }

  private <T> void bindOnDemandSqlObject(Binder binder, Class<T> sqlClass, Key<? extends JdbiService> serviceKey) {
    DynamicProxyBindingBuilder.bindDynamicProxy(binder, sqlClass)
            .initializedFrom(serviceKey, jdbi -> jdbi.onDemand(sqlClass));
  }

  public JdbiModule bindOnDemandSqlObject(Binder binder, Class<?>... sqlClasses) {
    binder.install(this);
    for (Class<?> sqlClass : sqlClasses) {
      bindOnDemandSqlObject(binder, sqlClass, serviceKey);
    }

    return this;
  }

  public Multibinder<JdbiPlugin> pluginBinder(Binder binder) {
    return pluginBinder(binder, bindingAnnotation);
  }

  public static Multibinder<JdbiPlugin> pluginBinder(Binder binder, Annotation databaseAnnotation) {
    return Multibinder.newSetBinder(binder, Key.get(JdbiPlugin.class, databaseAnnotation));
  }

  public LinkedBindingBuilder<JdbiPlugin> bindPlugin(Binder binder) {
    return pluginBinder(binder).addBinding();
  }

  public LinkedBindingBuilder<JdbiPlugin> bindPlugin(Binder binder, Annotation databaseAnnotation) {
    return pluginBinder(binder, databaseAnnotation).addBinding();
  }
}
