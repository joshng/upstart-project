package upstart.config;

import com.google.common.collect.ImmutableMap;
import upstart.services.UpstartService;
import com.typesafe.config.Config;
import org.immutables.value.Value;

import javax.inject.Inject;
import java.util.Map;

/**
 * Holds the {@link #activeConfig} and {#link #mappedObjects} for a {@link UpstartService}. Typical application
 * components will not use this class directly, but instead inject configuration-objects directly with {@link Inject @Inject}.
 */
@Value.Immutable
public abstract class UpstartApplicationConfig {
  static ImmutableUpstartApplicationConfig.Builder builder() {
    return ImmutableUpstartApplicationConfig.builder();
  }

  public abstract UpstartConfigProvider provider();

  public abstract Config activeConfig();

  abstract Map<ConfigKey<?>, UpstartConfigProvider.ConfigObject<?>> mappedObjects();

  public String describeConfig() {
    return ConfigDump.describe(activeConfig());
  }

  @Value.Lazy
  public Map<String, String> flattenedConfigProperties() {
    return ConfigDump.describeValues(activeConfig(), Integer.MAX_VALUE)
            .collect(ImmutableMap.toImmutableMap(ConfigDump.ValueDump::key, ConfigDump.ValueDump::value));
  }
}
