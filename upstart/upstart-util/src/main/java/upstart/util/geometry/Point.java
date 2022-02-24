package upstart.util.geometry;

import org.immutables.value.Value;
import upstart.util.annotations.Tuple;

//@Value.Immutable
@Tuple
public interface Point extends Translatable<Point> {
  Point ORIGIN = Point.of(0, 0);

  static Point of(int x, int y) {
    return ImmutablePoint.of(x, y);
  }

  static boolean colinear(Point p1, Point p2, Point p3) {
    return (p1.x() == p2.x() && p2.x() == p3.x())
            || (p1.y() == p2.y() && p2.y() == p3.y());
  }

  int x();
  int y();

  @Override
  default Point translate(int x, int y) {
    return Point.of(x() + x, y() + y);
  }

  default Point withY(int row) {
    return row == y() ? this : of(x(), row);
  }

  /**
   * @returns (max(x, other.x), max(y, other.y))
   */
  default Point maxRowCol(Point other) {
    if (other.y() >= y()) {
      if (other.x() >= x()) {
        return other;
      } else {
        return Point.of(x(), other.y());
      }
    } else if (other.x() > x()) {
      return Point.of(other.x(), y());
    } else {
      return this;
    }
  }

  /**
   * @returns (min(x, other.x), min(y, other.y))
   */
  default Point minRowCol(Point other) {
    if (other.y() <= y()) {
      if (other.x() <= x()) {
        return other;
      } else {
        return Point.of(x(), other.y());
      }
    } else if (other.x() < x()) {
      return Point.of(other.x(), y());
    } else {
      return this;
    }
  }
}
