package upstart.cluster;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import com.google.common.primitives.Ints;
import upstart.util.Identifier;
import org.immutables.value.Value;

@Value.Immutable(intern = true)
@Identifier
public abstract class PartitionId {
  @JsonCreator
  public static PartitionId of(int id) {
    return ImmutablePartitionId.of(id);
  }

  private static final HashFunction CONSISTENT_HASH_FUNCTION = Hashing.sha256();

  @JsonValue
  public abstract int id();

  @Value.Lazy
  public long partitionHashCode() {
    return CONSISTENT_HASH_FUNCTION.newHasher(Ints.BYTES)
            .putInt(id())
            .hash().padToLong();
  }

  @Override
  @Value.Lazy
  public String toString() {
    return String.format("Part%04d", id());
  }
}
