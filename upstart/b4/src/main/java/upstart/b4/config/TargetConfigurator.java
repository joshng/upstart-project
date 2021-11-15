package upstart.b4.config;

import upstart.b4.TargetExecutionConfig;
import upstart.b4.TargetInstanceId;
import upstart.util.Optionals;
import com.typesafe.config.Config;
import org.immutables.value.Value;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * Holds the logic for assembling configuration for a single {@link TargetInstanceId}
 */
@Value.Immutable
public interface TargetConfigurator extends TargetExecutionConfig {
  TargetConfigBuilder NO_CONFIG = (parentConfig) -> parentConfig;

  static ImmutableTargetConfigurator.Builder builder() {
    return ImmutableTargetConfigurator.builder();
  }

  static TargetConfigurator of(TargetInstanceId id, Config config) {
    return builder().target(id).config(config).build();
  }

  static TargetConfigurator of(TargetInstanceId id, TargetConfigBuilder builder) {
    return builder().target(id).configBuilder(builder).build();
  }

  static Stream<TargetConfigurator> mergeConfigurators(Stream<TargetConfigurator> configurators) {
    Map<TargetInstanceId, TargetConfigurator> mergedTargets = configurators
            .collect(Collectors.toMap(TargetConfigurator::target, Function.identity(), TargetConfigurator::merge, LinkedHashMap::new));
    return mergedTargets.values().stream();
  }

  TargetInstanceId target();
  Optional<Config> simpleConfig();
  TargetConfigBuilder configBuilder();

  default TargetConfigurator merge(TargetConfigurator other) {
    if (this.equals(other)) return this;

    checkArgument(target().equals(other.target()), "Mismatched targets");
    return builder()
            .from(other)
            .from(this) // use immutable-builder's Optional treatment to merge TargetExecutionConfig values
            .simpleConfig(Optionals.merge(simpleConfig(), other.simpleConfig(), Config::withFallback))
            .configBuilder(other.configBuilder().andThen(configBuilder()))
            .build();
  }

  interface Builder {
    ImmutableTargetConfigurator.Builder configBuilder(TargetConfigBuilder builder);
    ImmutableTargetConfigurator.Builder simpleConfig(Config config);

    default ImmutableTargetConfigurator.Builder config(Config config) {
      return config.isEmpty()
              ? configBuilder(NO_CONFIG)
              : configBuilder(new TargetConfigBuilder.SimpleConfigBuilder(config))
                      .simpleConfig(config);
    }
  }

  interface TargetConfigBuilder {
    Config configure(Config parentConfig);

    default TargetConfigBuilder andThen(TargetConfigBuilder other) {
      return (config) -> other.configure(configure(config)); //.resolve());
    }

    class SimpleConfigBuilder implements TargetConfigBuilder {
      private final Config config;

      public SimpleConfigBuilder(Config config) {
        this.config = config;
      }

      @Override
      public Config configure(Config other) {
        return config.withFallback(other);
      }

      @Override
      public String toString() {
        return "SimpleConfigBuilder{" +
                "config=" + config +
                '}';
      }
    }
  }
}
