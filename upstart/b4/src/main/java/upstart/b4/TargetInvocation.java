package upstart.b4;

import com.google.inject.Key;
import com.google.inject.name.Names;
import upstart.config.ConfigMappingException;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigRenderOptions;
import org.immutables.value.Value;

@Value.Immutable
public abstract class TargetInvocation implements TargetExecutionConfig {

  public static ImmutableTargetInvocation.Builder builder() {
    return ImmutableTargetInvocation.builder();
  }

  @Value.Auxiliary
  public abstract TargetInstanceId id();

  @Value.Auxiliary
  public abstract TargetSpec spec();

  @Value.Auxiliary
  public abstract Config config();

  @Value.Derived
  @Value.Auxiliary
  public Key<TargetRunner> runnerKey() {
    return Key.get(TargetRunner.class, Names.named(configString()));
  }

  @Value.Derived
  public String configString() {
    return id().displayName() + (hasConfigFlags() ? " " + isolatedConfig().root().render(ConfigRenderOptions.concise().setFormatted(true)) : "");
  }

  @Value.Lazy
  public Config isolatedConfig() {
    return config().hasPath(configPath()) ? config().resolve().getConfig(configPath()) : ConfigFactory.empty();
  }

  public boolean hasConfigFlags() {
    return !isolatedConfig().isEmpty();
  }

  public String configPath() {
    return id().instanceConfigPath();
  }

  @Value.Check
  void checkConfigs() {
    if (spec().configType().isEmpty() && hasConfigFlags()) {
      throw new ConfigMappingException(String.format("Target '%s' does not accept arguments:\n%s", id(), configString()));
    }
  }

  public enum Phases {
    CleanOnly(true, false),
    DirtyRun(false, true),
    CleanRun(true, true);

    public final boolean doClean;
    public final boolean doRun;

    Phases(boolean doClean, boolean doRun) {
      this.doClean = doClean;
      this.doRun = doRun;
    }
  }
}
