package upstart.b4.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import upstart.util.StringIdentifier;
import upstart.util.Tuple;
import com.typesafe.config.ConfigParseOptions;
import org.immutables.value.Value;

@Value.Immutable
@Tuple
public abstract class TemplateId extends StringIdentifier {
  @JsonCreator
  static TemplateId of(String id) {
    return ImmutableTemplateId.of(id);
  }

  @Value.Derived
  @Value.Auxiliary
  ConfigParseOptions configParseOptions() {
    return ConfigParseOptions.defaults().setOriginDescription("template " + value());
  }
}
