package upstart.config;

import io.upstartproject.hojack.ConfigMapper;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import upstart.UpstartStaticInitializer;

import java.util.Optional;
import java.util.function.UnaryOperator;

public class EnvironmentConfigBuilder implements TestConfigBuilder<EnvironmentConfigBuilder> {

  static {
    UpstartStaticInitializer.ensureInitialized();
  }

  private final ConfigMapper configMapper;
  private Config overrideConfig;

  public EnvironmentConfigBuilder() {
    this(UpstartEnvironment.ambientEnvironment());
  }

  public EnvironmentConfigBuilder(UpstartEnvironment baseEnvironment) {
    this(baseEnvironment.name(), baseEnvironment.configProvider().configMapper());
  }

  public EnvironmentConfigBuilder(String environmentName, ConfigMapper configMapper) {
    this(ConfigFactory.empty(), configMapper);
    setEnvironmentName(environmentName);
  }

  private EnvironmentConfigBuilder(Config initialConfig, ConfigMapper configMapper) {
    this.configMapper = configMapper;
    overrideConfig = initialConfig;
  }

  public EnvironmentConfigBuilder setEnvironmentName(String environmentName) {
    return overrideConfig(UpstartEnvironment.UPSTART_ENVIRONMENT, environmentName);
  }

  public String environmentName() {
    return overrideConfig.getString(UpstartEnvironment.UPSTART_ENVIRONMENT);
  }

  public EnvironmentConfigBuilder modifyConfig(UnaryOperator<Config> updater) {
    overrideConfig = updater.apply(overrideConfig);
    return this;
  }

  public Config getOverrideConfig() {
    return overrideConfig;
  }

  public EnvironmentConfigBuilder copy() {
    return new EnvironmentConfigBuilder(overrideConfig, configMapper().copy());
  }

  public HojackConfigProvider buildConfigProvider() {
    return buildConfigProvider(UpstartEnvironment.ambientEnvironment().configProvider());
  }

  public HojackConfigProvider buildConfigProvider(HojackConfigProvider baseConfigProvider) {
    return baseConfigProvider
            .withConfigMapper(configMapper())
            .withOverrideConfig(overrideConfig);
  }

  @Override
  public EnvironmentConfigBuilder rootConfigBuilder() {
    return this;
  }

  public Optional<String> relativePath() {
    return Optional.empty();
  }

  public ConfigMapper configMapper() {
    return configMapper;
  }
}
