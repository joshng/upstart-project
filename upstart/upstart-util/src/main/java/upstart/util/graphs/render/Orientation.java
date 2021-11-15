package upstart.util.graphs.render;

import upstart.util.geometry.Point;

import static com.google.common.base.Preconditions.checkArgument;

enum Orientation {
  Horizontal,
  Vertical;

  static Orientation between(Point a, Point b) {
    checkArgument(!a.equals(b), "Points are identical", a);
    if (a.x() == b.x()) {
      return Vertical;
    } else {
      checkArgument(a.y() == b.y(), "Points were diagonal", a, b);
      return Horizontal;
    }
  }
}
