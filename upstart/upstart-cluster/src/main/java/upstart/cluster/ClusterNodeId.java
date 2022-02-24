package upstart.cluster;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import upstart.util.annotations.Identifier;
import org.immutables.value.Value;

@Value.Immutable
@Identifier
public abstract class ClusterNodeId {
  @JsonCreator
  public static ClusterNodeId of(String sessionId) {
    return ImmutableClusterNodeId.of(sessionId);
  }

  @JsonValue
  public abstract String sessionId();

  public String toString() {
    return "NodeId{" + sessionId() + "}";
  }
}
