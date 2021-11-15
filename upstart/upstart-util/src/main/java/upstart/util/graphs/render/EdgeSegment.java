package upstart.util.graphs.render;

import upstart.util.Tuple;
import upstart.util.geometry.Point;
import upstart.util.geometry.Rectangle;
import org.immutables.value.Value;

@Value.Immutable
@Tuple
interface EdgeSegment {
  static EdgeSegment of(Point start, Point end) {
    return of(start, end, Orientation.between(start, end));
  }

  static EdgeSegment of(Point start, Point end, Orientation orientation) {
    return ImmutableEdgeSegment.of(start, end, orientation);
  }

  Point start();

  Point end();

  Orientation orientation();

  EdgeSegment withStart(Point start);

  EdgeSegment withEnd(Point end);

  default EdgeSegment withRow(int row) {
    return of(start().withY(row), end().withY(row));
  }

  default EdgeSegment withStartRow(int row) {
    return of(start().withY(row), end());
  }

  default EdgeSegment withEndRow(int row) {
    return of(start(), end().withY(row));
  }

  @Value.Derived
  @Value.Auxiliary
  default Rectangle rectangle() {
    return (start().x() < end().x() || start().y() < end().y())
            ? Rectangle.of(start(), end())
            : Rectangle.of(end(), start());
  }
}
