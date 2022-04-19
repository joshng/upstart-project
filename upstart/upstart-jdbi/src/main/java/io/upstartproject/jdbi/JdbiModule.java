package io.upstartproject.jdbi;

import com.google.inject.Binder;
import com.google.inject.Key;
import com.google.inject.PrivateModule;
import com.google.inject.TypeLiteral;
import com.google.inject.binder.LinkedBindingBuilder;
import com.google.inject.multibindings.Multibinder;
import com.zaxxer.hikari.HikariConfig;
import org.jdbi.v3.core.spi.JdbiPlugin;
import upstart.config.UpstartModule;
import upstart.proxy.DynamicProxyBindingBuilder;

import java.lang.annotation.Annotation;
import java.util.Set;

public class JdbiModule extends UpstartModule {
  private static final TypeLiteral<Set<JdbiPlugin>> PLUGIN_SET_TYPE = new TypeLiteral<>(){};
  private final Key<HikariJdbiService> serviceKey;
  private final Key<HikariConfig> configKey;

  public static JdbiModule named(String name) {
    return named(name, Key.get(HikariConfig.class, Databases.of(name)));
  }

  public static JdbiModule named(
          String name,
          final Key<HikariConfig> configKey
  ) {
    Databases annotation = Databases.of(name);
    return new JdbiModule(Key.get(HikariJdbiService.class, annotation), configKey) {
      @Override
      protected void configure() {
        super.configure();
        bindConfig(name, configKey);
      }
    };
  }

  public JdbiModule(
          Key<HikariJdbiService> serviceKey,
          Key<HikariConfig> configKey
  ) {
    super(serviceKey, configKey);
    this.serviceKey = serviceKey;
    this.configKey = configKey;
  }

  @Override
  protected void configure() {
    install(new PrivateModule() {
      @Override
      protected void configure() {
        bind(serviceKey).to(HikariJdbiService.class);
        expose(serviceKey);
        bind(HikariConfig.class).to(configKey);
        bind(PLUGIN_SET_TYPE).to(Key.get(PLUGIN_SET_TYPE, serviceKey.getAnnotation()));
      }
    });

    pluginBinder(binder());
    serviceManager().manage(serviceKey);
  }

  public static <T> void bindOnDemandSqlObject(Binder binder, Class<T> sqlClass, Key<? extends AbstractJdbiService> serviceKey) {
    DynamicProxyBindingBuilder.bindDynamicProxy(binder, sqlClass)
            .initializedFrom(serviceKey, jdbi -> jdbi.onDemand(sqlClass));
  }

  public JdbiModule bindOnDemandSqlObject(Binder binder, Class<?> sqlClass) {
    bindOnDemandSqlObject(binder, sqlClass, serviceKey);
    return this;
  }

  public Multibinder<JdbiPlugin> pluginBinder(Binder binder) {
    return pluginBinder(binder, serviceKey.getAnnotation());
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
