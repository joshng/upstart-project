package upstart.b4.config;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.common.collect.ImmutableList;
import com.google.inject.Module;
import upstart.b4.TargetExecutionConfig;
import upstart.b4.TargetInstanceId;
import upstart.b4.TargetName;
import upstart.b4.TargetSpec;
import upstart.util.MoreStrings;
import upstart.util.PairStream;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigList;
import com.typesafe.config.ConfigObject;
import com.typesafe.config.ConfigParseOptions;
import com.typesafe.config.ConfigValue;
import org.immutables.value.Value;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Value.Immutable
@JsonDeserialize(builder = ImmutableTargetConfig.Builder.class)
@Value.Style(deepImmutablesDetection = true)
public abstract class TargetConfig {
  static final String TASKS_FIELD = "tasks";

  public static ImmutableTargetConfig.Builder builder() {
    return ImmutableTargetConfig.builder();
  }

  public static String joinPaths(String prefix, String suffix) {
    return prefix + "." + suffix;
  }

  public static String joinPaths(String... segments) {
    return String.join(".", segments);
  }

  public abstract Optional<String> description();

  public abstract Map<TargetInstanceId, ConfigValue> taskConfig();

  public abstract Optional<ConfigValue> dependencies();

  public abstract Optional<ConfigValue> tasks();

  public abstract Optional<Class<? extends Module>> module();

  public TargetSpec buildSpec(TargetName name) {

    return TargetSpec.builder(name)
            .taskConfigs(parseTaskConfigs(name.value() + ".taskConfig", PairStream.of(taskConfig())))
            .description(description())
            .dependencies(dependencies().map(depNode -> parseTargets(name, depNode)).orElse(ImmutableList.of()))
            .tasks(tasks().map(tasksNode -> parseTargets(name, tasksNode)).orElse(ImmutableList.of()))
            .moduleClass(module())
            .build();
  }

  @SuppressWarnings("unchecked")
  private static List<TargetConfigurator> parseTargets(TargetName name, ConfigValue node) {
    String argSource = name.value() + "-config";
    return switch (node.valueType()) {
      case LIST -> CommandLineParser.parseCommands((List<String>) node.unwrapped(), TargetExecutionConfig.DEFAULT, argSource);
      case OBJECT -> parseTaskConfigs(argSource, PairStream.of(((ConfigObject) node))
              .mapKeys((Function<String, TargetInstanceId>) TargetInstanceId::of));
      case STRING -> CommandLineParser.parseCommands(MoreStrings.splitOnWhitespace((String) node.unwrapped())
              .collect(Collectors.toList()), TargetExecutionConfig.DEFAULT, argSource);
      default -> throw new IllegalArgumentException("Invalid target config: " + node);
    };
  }

  private static List<TargetConfigurator> parseTaskConfigs(String argSource, PairStream<TargetInstanceId, ConfigValue> targetConfigs) {
    return targetConfigs
            .mapValues((k, v) -> parseConfig(v, argSource + "[" + k.displayName() + "]"))
            .mapValues((k, config) -> config.atPath(k.instanceConfigPath()))
            .map(TargetConfigurator::of)
            .collect(Collectors.toList());
  }

  private static Config parseConfig(ConfigValue node, String originDescription) {
    return switch (node.valueType()) {
      case OBJECT -> ((ConfigObject) node).toConfig();
      case STRING -> ConfigFactory.parseString((String) node.unwrapped(), ConfigParseOptions.defaults()
              .setOriginDescription(originDescription));
      default -> throw new IllegalArgumentException("Bad config for target " + originDescription + ": " + node);
    };
  }
}
