package upstart.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableList;
import io.upstartproject.hojack.ConfigMapper;
import io.upstartproject.hojack.HojackConfigMapper;
import upstart.UpstartDeploymentStage;
import upstart.util.collect.Optionals;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValue;
import org.immutables.value.Value;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;

@Value.Immutable
public abstract class HojackConfigProvider extends UpstartConfigProvider {

  private static final LoadingCache<String, Config> REFERENCE_CONFIGS = CacheBuilder.newBuilder()
          .build(new CacheLoader<>() {
            @Override
            public Config load(String key) {
              Config defaults = ConfigFactory.parseResourcesAnySyntax(referenceConfigPath(key));
              return defaults.atPath(key);
            }
          });

  public static ImmutableHojackConfigProvider.Builder builder() {
    return ImmutableHojackConfigProvider.builder();
  }

  public static Config getReferenceConfig(String path) {
    return REFERENCE_CONFIGS.getUnchecked(path);
  }

  public static String referenceConfigPath(String key) {
    return "upstart-defaults/" + key;
  }

  public abstract Config baseConfig();

  @Override
  @Value.Default
  public UpstartDeploymentStage deploymentStage() {
    return UpstartDeploymentStage.prod;
  }

  @Value.Lazy
  Config resolvedBaseConfig() {
    return baseConfig().resolve();
  }

  @Value.Default
  public ConfigMapper configMapper() {
    return DefaultMapperHolder.DEFAULT_CONFIG_MAPPER;
  }

  public HojackConfigProvider withOverrideConfig(Config overrideConfig) {
    return overrideConfig.isEmpty() ? this : builder().from(this).baseConfig(overrideConfig.withFallback(baseConfig())).build();
  }

  public abstract HojackConfigProvider withConfigMapper(ConfigMapper configMapper);

  @Override
  public Optional<String> getOptionalString(String path) {
    return getIfPresent(path, Config::getString);
  }

  @Override
  public Optional<Boolean> getOptionalBoolean(String path) {
    return getIfPresent(path, Config::getBoolean);
  }

  @Override
  public List<String> getStringList(String path) {
    return getIfPresent(path, Config::getStringList).orElse(ImmutableList.of());
  }

  @Override
  public <T> ConfigObject<T> loadConfigObject(ConfigKey<T> key) {
    Config objectConfig = resolvedBaseConfig()
            .withFallback(getReferenceConfig(key.configPath()))
            .resolve()
            .withOnlyPath(key.configPath());

    T value = mapConfigValue(key.configPath(), key.mappedType().getType(), objectConfig);
    return ConfigObject.of(value, objectConfig);
  }

  private <T> Optional<T> getIfPresent(String path, BiFunction<Config, String, T> accessor) {
    return Optionals.onlyIfFrom(baseConfig().hasPath(path), () -> accessor.apply(baseConfig(), path));
  }

  @SuppressWarnings({"unchecked"})
  private <T> T mapConfigValue(String path, Type paramType, Config config) {
    try {
      return (T) PrimitiveConfigExtractor.extractConfigValue(config, paramType, path)
              .orElseGet(() -> mapConfig(path, paramType, config));
    } catch (Exception e) {
      throw new ConfigMappingException("Error loading config-path '" + path + "' into type: " + paramType, e);
    }
  }

  private <T> T mapConfig(String path, Type paramType, Config config) {
    ConfigValue value = config.getValue(path);
    try {
      return configMapper().mapConfigValue(value, paramType);
    } catch (Exception e) {
      // TODO: if caused by com.fasterxml.jackson.databind.exc.ValueInstantiationException,
      // we could parse the message, and throw a richer exception that could allow prompting for missing values/retrying.
      // message looks like this: "Cannot build KubeApplyConfig, some of required attributes are not set [namespace, spec]"
      String sourceCleanedConfig = "Invalid config:\n" + rewriteTargetResourcePaths(value.render());
      throw new ConfigMappingException(sourceCleanedConfig, e);
    }
  }

  public static String rewriteTargetResourcePaths(String msg) {
    return msg
            .replaceAll("/target/classes/", "/src/main/resources/")
            .replaceAll("/target/test-classes/", "/src/test/resources/");
  }


  private static class DefaultMapperHolder {
    static ObjectMapper DEFAULT_OBJECT_MAPPER = ObjectMapperFactory.buildAmbientObjectMapper();
    static HojackConfigMapper DEFAULT_CONFIG_MAPPER = new HojackConfigMapper(DEFAULT_OBJECT_MAPPER);
  }
}
