package upstart.util.geometry;

import upstart.util.Tuple;
import org.immutables.value.Value;

import static com.google.common.base.Preconditions.checkArgument;

@Value.Immutable
@Tuple
public interface Dimension {
  static Dimension of(int width, int height) {
    return ImmutableDimension.of(width, height);
  }

  int width();
  int height();

  @Value.Check
  default void checkNonNegative() {
    assert width() >= 0 && height() >= 0 : "Dimensions cannot be negative: " + this;
  }
}
