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

public class HikariJdbiModule extends JdbiModule {
  private final Annotation bindingAnnotation;
  private final Key<HikariConfig> configKey;


  public static HikariJdbiModule fromUpstartHikariConfig(String configPath) {
    return fromUpstartHikariConfig(configPath, Databases.of(configPath));
  }

  public static HikariJdbiModule fromUpstartHikariConfig(String configPath, Annotation bindingAnnotation) {
    Key<HikariConfig> hikariConfigKey = Key.get(HikariConfig.class, bindingAnnotation);
    return new HikariJdbiModule(hikariConfigKey, bindingAnnotation) {
      @Override
      protected void configure() {
        bindConfig(configPath, hikariConfigKey);

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
    install(new AnnotationKeyedPrivateModule(bindingAnnotation, HikariJdbiModule.class) {
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
      config.validate();
      this.config = config;
    }

    @Override
    public Jdbi buildJdbi() {
      return Jdbi.create(new HikariDataSource(config));
    }
  }
}
