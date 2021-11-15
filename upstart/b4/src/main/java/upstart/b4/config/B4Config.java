package upstart.b4.config;

import upstart.b4.B4TargetGenerator;
import upstart.b4.TargetInstanceId;
import upstart.b4.TargetName;
import upstart.config.annotations.ConfigPath;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigValue;
import org.immutables.value.Value;

import java.util.Map;

@Value.Immutable
//@JsonDeserialize(as = ImmutableSupConfig.class)
//@ConfigPath("b4")
public interface B4Config {
  static ImmutableB4Config.Builder builder() {
    return ImmutableB4Config.builder();
  }

  Map<TemplateId, TemplateConfig> targetTemplates();

  Map<String, GeneratorConfig> generators();
  Map<TargetName, ConfigValue> functions();

  Map<TargetName, ConfigValue> targets();

  Map<TargetInstanceId, ConfigValue> tasks();

  @ConfigPath("program-name")
  String programName();

  @Value.Immutable
  interface GeneratorConfig {
    static ImmutableGeneratorConfig.Builder builder() {
      return ImmutableGeneratorConfig.builder();
    }

    Class<? extends B4TargetGenerator<?>> impl();
    Config config();
  }

  interface Templates {
    Map<TemplateId, TemplateConfig> targetTemplates();
  }
}
