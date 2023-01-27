package upstart.config;

import com.typesafe.config.ConfigParseOptions;
import io.upstartproject.hojack.ConfigMapper;
import io.upstartproject.hojack.HojackConfigMapper;
import upstart.log.UpstartLogConfig;
import upstart.util.SelfType;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import java.util.Collections;
import java.util.Optional;
import java.util.function.Consumer;

public interface TestConfigBuilder<S extends TestConfigBuilder<S>> extends SelfType<S> {
  String TEST_OVERRIDE_ORIGIN = "test EnvironmentConfig";
  ConfigParseOptions OVERRIDE_PARSE_OPTIONS = ConfigParseOptions.defaults().setOriginDescription(TEST_OVERRIDE_ORIGIN);
  String DEFAULT_CONFIG_PLACEHOLDER = "<test-placeholder>";

  default S setLogThreshold(Class<?> logger, UpstartLogConfig.LogThreshold threshold) {
    return setLogThreshold(logger.getName(), threshold);
  }

  default S setLogThreshold(String category, UpstartLogConfig.LogThreshold threshold) {
    return overrideConfig("upstart.log.levels.\"" + category + "\"", threshold.toString());
  }

  default S setPlaceholder(String configPath) {
    return overrideConfig(configPath, DEFAULT_CONFIG_PLACEHOLDER);
  }

  default S overrideConfig(String configPath, String value) {
    return overrideConfig(ConfigFactory.parseMap(Collections.singletonMap(configPath, value), TEST_OVERRIDE_ORIGIN));
  }

  default S overrideConfig(String configPath, Object value) {
    return overrideConfig(configMapper().asConfig(configPath, value, TEST_OVERRIDE_ORIGIN));
  }

  default S overrideConfig(String hocon) {
    return overrideConfig(ConfigFactory.parseString(hocon, OVERRIDE_PARSE_OPTIONS));
  }

  default S overrideConfig(Config config) {
    rootConfigBuilder().modifyConfig(existing -> relativePath().map(config::atPath).orElse(config).withFallback(existing));
    return self();
  }

  default S subConfig(String pathPrefix, Consumer<SubConfigBuilder> configBuilder) {
    SubConfigBuilder subConfigBuilder = new SubConfigBuilder(relativePath().map(path -> path + "." + pathPrefix).orElse(pathPrefix), rootConfigBuilder());
    configBuilder.accept(subConfigBuilder);
    return self();
  }

  ConfigMapper configMapper();


  Optional<String> relativePath();
  EnvironmentConfigBuilder rootConfigBuilder();

  default S registerConfigModule(com.fasterxml.jackson.databind.Module module) {
    ((HojackConfigMapper)configMapper()).registerModule(module);
    return self();
  }

  class SubConfigBuilder implements TestConfigBuilder<SubConfigBuilder> {
    private final EnvironmentConfigBuilder rootConfigBuilder;
    private final Optional<String> relativePath;

    public SubConfigBuilder(String relativePath, EnvironmentConfigBuilder rootConfigBuilder) {
      this.relativePath = Optional.of(relativePath);
      this.rootConfigBuilder = rootConfigBuilder;
    }

    @Override
    public Optional<String> relativePath() {
      return relativePath;
    }

    @Override
    public EnvironmentConfigBuilder rootConfigBuilder() {
      return rootConfigBuilder;
    }

    @Override
    public ConfigMapper configMapper() {
      return rootConfigBuilder.configMapper();
    }
  }
}
