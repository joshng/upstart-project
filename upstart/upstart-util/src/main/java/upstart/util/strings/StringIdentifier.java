package upstart.util.strings;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonValue;
import org.immutables.value.Value;

import java.util.regex.Pattern;

import static com.google.common.base.Preconditions.checkArgument;

public abstract class StringIdentifier {
  public static final Pattern VALID_STRING_IDENTIFIER = Pattern.compile("[a-zA-Z0-9_.-]+");

  @JsonValue
  public abstract String value();

  @Value.Check
  protected void checkValidValue() {
    checkArgument(validationPattern().matcher(value()).matches(), "Invalid identifier, must match %s: %s", validationPattern(), value());
  }

  @JsonIgnore
  protected Pattern validationPattern() {
    return VALID_STRING_IDENTIFIER;
  }
}
