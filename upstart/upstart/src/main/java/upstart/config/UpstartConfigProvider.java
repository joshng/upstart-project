package upstart.config;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import io.upstartproject.hojack.HojackConfigMapper;
import upstart.UpstartService;
import upstart.UpstartDeploymentStage;
import upstart.util.annotations.Tuple;
import com.typesafe.config.Config;
import org.immutables.value.Value;

import java.util.List;
import java.util.Optional;

/**
 * Holds the configuration required to start a {@link UpstartService}.
 *
 * Most applications will not need to worry about this interface at all: if the default {@link UpstartEnvironment} and
 * {@link HojackConfigMapper} implementations are sufficient, then the no-arg {@link UpstartService#builder()}
 * method allows bypassing this concern by using a default {@link HojackConfigMapper}.<p/>
 *
 * @see HojackConfigProvider
 * @see UpstartEnvironment#defaultConfigProvider()
 */
public abstract class UpstartConfigProvider {
  private final LoadingCache<ConfigKey<?>, ConfigObject<?>> loadedObjects = CacheBuilder.newBuilder()
          .build(CacheLoader.from(this::loadConfigObject));

  public abstract UpstartDeploymentStage deploymentStage();

  public abstract Optional<String> getOptionalString(String path);

  public abstract Optional<Boolean> getOptionalBoolean(String path);

  public String getString(String path) {
    return getOptionalString(path).orElseThrow(() -> new IllegalStateException("No configuration value found for path '" + path + "'"));
  }

  public abstract List<String> getStringList(String path);

  @SuppressWarnings("unchecked")
  public <T> ConfigObject<T> getConfigObject(ConfigKey<T> key) {
    return (ConfigObject<T>) loadedObjects.getUnchecked(key);
  }

  protected abstract <T> ConfigObject<T> loadConfigObject(ConfigKey<T> key);

  @Value.Immutable
  @Tuple
  public abstract static class ConfigObject<T> {
    static <T> ConfigObject<T> of(T mappedObject, Config objectConfig) {
      return ImmutableConfigObject.of(mappedObject, objectConfig);
    }

    public abstract T mappedObject();
    public abstract Config objectConfig();
  }
}
