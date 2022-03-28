package upstart.config;

import com.google.inject.Binder;
import com.google.inject.Key;
import upstart.services.UpstartService;
import upstart.UpstartModuleExtension;
import upstart.config.annotations.ConfigPath;
import upstart.util.collect.MoreStreams;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import upstart.util.concurrent.ThreadLocalReference;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import static com.google.common.base.Preconditions.checkState;

/**
 * A {@link ThreadLocal} object which sponsors and deduplicates the configuration-objects which are requested with
 * {@link UpstartModuleExtension#bindConfig}, making them accessible to guice-managed objects within an {@link UpstartService}.<p/>
 *
 * Also verifies that mapped {@link ConfigPath ConfigPaths} do not overlap, to prevent redundant (and possibly conflicting)
 * configs from being loaded.<p/>
 *
 * Produces an {@link UpstartApplicationConfig} to expose the fully-resolved configuration values in use by the
 * application.
 */
public class UpstartConfigBinder {
  private static final ThreadLocalReference<UpstartConfigBinder> APPLICATION_CONFIG_BINDER = new ThreadLocalReference<>();
  private final UpstartConfigProvider configProvider;
  private final Map<Key<?>, Object> boundKeys = new HashMap<>();
  private final Set<String> boundConfigPaths = new HashSet<>();
  private final Map<ConfigKey<?>, UpstartConfigProvider.ConfigObject<?>> boundConfigObjects = new HashMap<>();

  private Config loadedConfig = ConfigFactory.empty();
  private UpstartApplicationConfig finalConfig;

  private UpstartConfigBinder(UpstartConfigProvider configProvider) {
    this.configProvider = configProvider;
  }

  public static <T> T withBinder(UpstartConfigProvider config, Supplier<T> block) {
    return APPLICATION_CONFIG_BINDER.contextWithValue(new UpstartConfigBinder(config)).getInContext(block);
  }

  public static UpstartConfigBinder get() {
    UpstartConfigBinder bindings = APPLICATION_CONFIG_BINDER.get();
    checkState(bindings != null, "No UpstartBinder in scope for this thread (this module must be installed via UpstartApplication.Builder)");
    return bindings;
  }

  public UpstartConfigProvider configProvider() {
    return configProvider;
  }

  public <T> T bindConfig(Binder binder, Class<T> configClass) {
    ConfigKey<T> configKey = ConfigKey.of(configClass);
    Key<T> key = Key.get(configKey.mappedType());
    return bindConfig(binder, key, configKey);
  }

  @SuppressWarnings("unchecked")
  public <T> T bindConfig(Binder binder, Key<? super T> key, ConfigKey<T> configKey) {
    return (T) boundKeys.computeIfAbsent(key, k -> {
      T mappedValue = configKey.cast(boundConfigObjects.computeIfAbsent(configKey, this::load).mappedObject());
      binder.bind(key).toInstance(mappedValue);
      return mappedValue;
    });
  }

  public synchronized UpstartApplicationConfig finalUpstartConfig() {
    if (finalConfig == null) {
      finalConfig = UpstartApplicationConfig.builder()
              .provider(configProvider)
              .activeConfig(loadedConfig)
              .mappedObjects(boundConfigObjects)
              .build();
    }
    return finalConfig;
  }

  private synchronized <T> UpstartConfigProvider.ConfigObject<T> load(ConfigKey<T> key) {
    checkState(finalConfig == null, "Config was frozen");
    checkForOverlap(key);

    UpstartConfigProvider.ConfigObject<T> configObject = configProvider.getConfigObject(key);
    boundConfigPaths.add(key.configPath());
    loadedConfig = loadedConfig.withFallback(configObject.objectConfig());
    return configObject;
  }

  private void checkForOverlap(ConfigKey<?> key) {
    MoreStreams.scan(
            "",
            key.pathSegments().stream(),
            (memo, segment) -> memo.isEmpty() ? segment : memo + '.' + segment
    ).filter(boundConfigPaths::contains)
            .findFirst()
            .ifPresent(conflict -> throwKeyConflictException(conflict, key));

    if (loadedConfig.hasPath(key.configPath())) {
      // path is present in the config, so it must be a prefix of some already-loaded path. find the conflict:
      boundConfigPaths.stream()
              .filter(k -> k.startsWith(key.contentPathPrefix()))
              .findFirst()
              .map(conflict -> throwKeyConflictException(conflict, key))
              .orElseThrow(() -> new AssertionError("Bug: expected to find conflicting key for " + key));
    }
  }

  private static <T> T throwKeyConflictException(String conflict, ConfigKey<?> key) {
    throw new IllegalArgumentException("ConfigPaths must not overlap: already-loaded '" + conflict + "' conflicts with " + key);
  }
}
