package upstart.cluster.zk;

import com.fasterxml.jackson.annotation.JsonCreator;
import upstart.util.annotations.Identifier;
import upstart.util.StringIdentifier;
import org.immutables.value.Value;

import java.util.regex.Pattern;

@Value.Immutable
@Identifier
public abstract class ClusterId extends StringIdentifier {
  public static final Pattern VALIDATION_PATTERN = Pattern.compile("[a-zA-Z0-9_.@-]+");
  @JsonCreator
  public static ClusterId of(String id) {
    return ImmutableClusterId.of(id);
  }

  @Override
  protected Pattern validationPattern() {
    return VALIDATION_PATTERN;
  }
}
