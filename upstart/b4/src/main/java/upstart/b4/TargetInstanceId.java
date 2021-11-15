package upstart.b4;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import upstart.b4.config.TargetConfig;
import upstart.b4.config.TargetConfigurator;
import upstart.util.MoreStrings;
import com.typesafe.config.Config;
import org.immutables.value.Value;

import java.util.function.Function;

import static com.google.common.base.Preconditions.checkArgument;

@Value.Immutable
public abstract class TargetInstanceId {

  public static final String TASK_CONFIG_ROOT = "b4.tasks";

  @JsonCreator
  public static TargetInstanceId of(String id) {
    int idOffset = id.indexOf('<');
    if (idOffset > 0) {
      checkArgument(id.endsWith(">"), "Invalid target-id", id);
      String target = id.substring(0, idOffset);
      String instanceId = id.substring(idOffset + 1, id.length() - 1);
      return of(TargetName.of(target), instanceId);
    } else if ((idOffset = id.indexOf(':')) >= 0) {
      return MoreStrings.splitAroundCharAt(id, idOffset).map2(TargetInstanceId::of);
    } else {
      return TargetName.of(id).defaultInstance();
    }
  }

  public static TargetInstanceId of(String name, String id) {
    return of(TargetName.of(name), id);
  }
  public static TargetInstanceId of(TargetName name, String id) {
    return builder().targetName(name).instanceId(id).build();

  }

  public static ImmutableTargetInstanceId.Builder builder() {
    return ImmutableTargetInstanceId.builder();
  }

  @JsonValue
  @Value.Default
  public String displayName() {
    return format(id -> ":" + id);
  }

  public boolean isDefaultInstance() {
    return instanceId().isEmpty();
  }

  public abstract String instanceId();
  public abstract TargetName targetName();

  @Value.Lazy
  public String instanceConfigPath() {
    return TargetConfig.joinPaths(TASK_CONFIG_ROOT, format(instanceId -> "<" + instanceId + ">"));
  }

  private String format(Function<String, String> instanceIdFormatter) {
    return isDefaultInstance() ? targetName().displayName() : targetName().displayName() + instanceIdFormatter.apply(instanceId());
  }

  public String instanceConfigPath(String field) {
    return TargetConfig.joinPaths(instanceConfigPath(), field);
  }

  public Config applyReferenceConfig(Config config) {
    Config reference = targetName().referenceConfig();
    if (reference.isEmpty()) return config;

    String refPath = targetName().configPath();
    return config.withFallback(
            config.withFallback(reference)
                    .getConfig(refPath)
                    .atPath(instanceConfigPath())
    );
  }

  public TargetConfigurator emptyConfigurator() {
    return TargetConfigurator.of(this, TargetConfigurator.NO_CONFIG);
  }


  @Override
  public String toString() {
    return displayName();
  }
}
