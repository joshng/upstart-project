package upstart.b4.config;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.common.collect.ImmutableList;
import upstart.b4.TargetName;
import upstart.util.strings.MoreStrings;
import upstart.util.collect.PairStream;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValue;
import org.immutables.value.Value;

import java.util.Map;
import java.util.regex.Pattern;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

@Value.Immutable
@JsonDeserialize(as = ImmutableTemplateConfig.class)
public abstract class TemplateConfig {
  private static final String NAME_TOKEN = "NAME";
  private static final Pattern TOKEN_PATTERN = Pattern.compile("(?<!\\\\)%\\{(.*?)}");

  public abstract ImmutableList<TemplateId> mixins();
  public abstract String template();
  public abstract Map<String, Map<String, String>> targets();

  PairStream<TargetName, ConfigValue> templatizeTargets(TemplateId templateId, Map<TemplateId, TemplateConfig> templates) {
    return PairStream.of(targets())
            .flatMapPairs((instanceName, values) -> templatizeInstance(templateId, instanceName, values, templates).root().entrySet().stream())
            .mapKeys(TargetName::of);
  }

  Config templatizeInstance(
          TemplateId templateId,
          String instanceName,
          Map<String, String> values,
          Map<TemplateId, TemplateConfig> templates
  ) {
    String configStr = MoreStrings.interpolateTokens(template(), TOKEN_PATTERN, matcher -> {
      String key = matcher.group(1);
      return key.equals(NAME_TOKEN)
              ? instanceName
              // TODO: this error-message could use more context for `mixin` calls
              : checkNotNull(values.get(key), "Unrecognized template token '%s' for template '%s.%s'", key, templateId, instanceName);
    });

    Config config = ConfigFactory.parseString(configStr, templateId.configParseOptions());

    // reverse to give first mixin lower precedence in case of overlap (as one might expect with last-write-wins)
    return mixins().reverse().stream()
            .map(parent -> {
              TemplateConfig parentTemplate = checkNotNull(templates.get(parent), "Template '%s' cannot extend unrecognized template '%s'", templateId, parent);
              return config.withFallback(parentTemplate.templatizeInstance(parent, instanceName, values, templates));
            }).reduce(config, Config::withFallback);
  }

  @Value.Check
  void checkInstanceConfigs() {
    targets().forEach((name, values) -> checkArgument(!values.containsKey(NAME_TOKEN), "Illegal template instance-config key '%s' in target-template '%s'", NAME_TOKEN, name));
  }
}
