package io.upstartproject.jdbi;

import com.google.inject.Key;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.jdbi.v3.core.Jdbi;
import upstart.guice.AnnotationKeyedPrivateModule;
import upstart.guice.PrivateBinding;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.lang.annotation.Annotation;
import java.util.Properties;

public class HikariJdbiModule extends JdbiModule {
  private final Annotation bindingAnnotation;
  private final Key<HikariConfig> configKey;


  public static HikariJdbiModule fromUpstartHikariConfig(String configPath) {
    return fromUpstartHikariConfig(configPath, Databases.of(configPath));
  }

  public static HikariJdbiModule fromUpstartHikariConfig(String configPath, Annotation bindingAnnotation) {
    Key<Properties> propertiesKey = Key.get(Properties.class, bindingAnnotation);
    Key<HikariConfig> configKey = Key.get(HikariConfig.class, bindingAnnotation);
    return new HikariJdbiModule(configKey, bindingAnnotation) {
      @Override
      protected void configure() {
        Properties properties = bindConfig(configPath, propertiesKey);
        HikariConfig config = new HikariConfig(properties);
        try {
          config.validate();
        } catch (Exception e) {
          throw new RuntimeException("Invalid Hikari database config at '" + configPath + "': " + e.getMessage(), e);
        }
        bind(configKey).toInstance(config);

        super.configure();
      }
    };
  }

  public HikariJdbiModule(Key<HikariConfig> configKey, Annotation bindingAnnotation) {
    super(Key.get(HikariJdbiInitializer.class, bindingAnnotation), bindingAnnotation);
    deduplicateBy(configKey);

    this.bindingAnnotation = bindingAnnotation;
    this.configKey = configKey;
  }

  @Override
  protected void configure() {
    install(new AnnotationKeyedPrivateModule(bindingAnnotation, HikariJdbiInitializer.class) {
      @Override
      protected void configurePrivateScope() {
        bindPrivateBinding(HikariConfig.class).to(configKey);
      }
    });
    super.configure();
  }

  @Singleton
  public static class HikariJdbiInitializer implements JdbiService.JdbiInitializer {
    private final HikariConfig config;

    @Inject
    public HikariJdbiInitializer(@PrivateBinding HikariConfig config) {
      this.config = config;
      this.config.validate();
    }

    @Override
    public Jdbi buildJdbi() {
      return Jdbi.create(new HikariDataSource(config));
    }
  }
}
