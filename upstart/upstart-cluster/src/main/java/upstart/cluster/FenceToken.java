package upstart.cluster;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import upstart.util.annotations.Tuple;
import org.immutables.value.Value;

@Value.Immutable
@Tuple
public interface FenceToken {
  @JsonCreator
  static FenceToken of(long token) {
    return ImmutableFenceToken.of(token);
  }

  @JsonValue
  long precedence();
}
