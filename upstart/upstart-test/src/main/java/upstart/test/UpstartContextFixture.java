package upstart.test;

import upstart.config.EnvironmentConfig;
import upstart.config.EnvironmentConfigFixture;
import upstart.config.TestConfigBuilder;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@EnvironmentConfig.Fixture(UpstartContextFixture.Values.class)
public @interface UpstartContextFixture {
  class Values implements EnvironmentConfigFixture {
    @Override
    public void applyEnvironmentValues(TestConfigBuilder<?> config) {
      config.subConfig("upstart.context", context -> context
              .setPlaceholder("application")
              .setPlaceholder("owner"));
    }
  }
}
