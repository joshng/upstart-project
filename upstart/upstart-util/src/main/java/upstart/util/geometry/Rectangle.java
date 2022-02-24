package upstart.util.geometry;

import upstart.util.annotations.Tuple;
import org.immutables.value.Value;

import static com.google.common.base.Preconditions.checkArgument;

@Value.Immutable
@Tuple
public interface Rectangle extends Translatable<Rectangle> {
  static Rectangle of(Point topLeft, Dimension dimension) {
    Point bottomRight = Point.of(topLeft.x() + dimension.width() - 1, topLeft.y() + dimension.height() - 1);
    return of(topLeft, bottomRight);
  }

  static Rectangle of(Point topLeft, Point botRight) {
    return ImmutableRectangle.of(topLeft, botRight);
  }

  Point topLeft();
  Point bottomRight();

  default Point topRight() {
    return Point.of(bottomRight().x(), topLeft().y());
  }

  default Point bottomLeft() {
    return Point.of(topLeft().x(), bottomRow());
  }

  default int height() {
    return bottomRight().y() - topLeft().y() + 1;
  }

  default int width() {
    return bottomRight().x() - topLeft().x() + 1;
  }

  default int topRow() {
    return topLeft().y();
  }

  default int bottomRow() {
    return bottomRight().y();
  }

  default int leftColumn() {
    return topLeft().x();
  }

  default int rightColumn() {
    return bottomRight().x();
  }

  default Dimension dimension() {
    return Dimension.of(width(), height());
  }

  @Override
  default Rectangle translate(int x, int y) {
    return of(topLeft().translate(x, y), bottomRight().translate(x, y));
  }

  default boolean contains(Point point) {
    return containsRow(point.y()) && containsColumn(point.x());
  }

  default boolean containsColumn(int column) {
    return column >= leftColumn() && column <= rightColumn();
  }

  default boolean containsRow(int row) {
    return row >= topRow() && row <= bottomRow();
  }

  default boolean contains(Rectangle rectangle) {
    return contains(rectangle.topLeft()) && contains(rectangle.bottomRight());
  }

  default boolean intersects(Rectangle other) {
    return !isDisjoint(other);
  }

  default boolean isDisjoint(Rectangle other) {
    return rightColumn() < other.leftColumn()
            || other.rightColumn() < leftColumn()
            || bottomRow() < other.topRow()
            || other.bottomRow() < topRow();
  }

  @Value.Check
  default void checkOrientations() {
    assert topRow() <= bottomRow() && leftColumn() <= rightColumn() : "top/bottom or left/right are reversed: " + this;
  }
}
