package upstart.util.graphs.render;

import upstart.util.geometry.Dimension;
import upstart.util.geometry.Point;

import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class CharGrid {
  private final char[][] chars;

  CharGrid(Dimension dimension) {
    int rows = dimension.height();
    int columns = dimension.width();
    chars = new char[rows][];
    for (int i = 0; i < rows; i++) {
      char[] row = chars[i] = new char[columns];
      Arrays.fill(row, ' ');
    }
  }

  CharGrid put(Point point, char c) {
    return put(point.x(), point.y(), c);
  }

  CharGrid put(int x, int y, char c) {
    chars[y][x] = c;
    return this;
  }

  void putHorizontalLine(Point leftStartPoint, int endXExclusive) {
    char[] row = chars[leftStartPoint.y()];
    for (int x = leftStartPoint.x(); x < endXExclusive; x++) {
      row[x] = '-';
    }
  }

  void putVerticalLine(Point topStartPoint, int endYExclusive) {
    int x = topStartPoint.x();
    for (int y = topStartPoint.y(); y < endYExclusive; y++) {
      chars[y][x] = '|';
    }
  }

  void putString(Point startPoint, String str) {
    char[] content = str.toCharArray();
    System.arraycopy(content, 0, chars[startPoint.y()], startPoint.x(), content.length);
  }

  public String render() {
    return Stream.of(chars).map(String::new).collect(Collectors.joining("\n"));
  }
}
