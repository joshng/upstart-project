package upstart.util.graphs.render;

import com.google.common.base.Strings;
import com.google.common.collect.Streams;
import upstart.util.PairStream;
import upstart.util.geometry.Dimension;
import upstart.util.geometry.Point;
import upstart.util.geometry.Rectangle;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

class VertexDrawing<T> implements Drawing<T> {
  private final VertexBox<T> vertex;
  private Rectangle rectangle;

  VertexDrawing(VertexBox<T> vertex, Rectangle rectangle) {
    this.vertex = vertex;
    this.rectangle = rectangle;
  }

  Rectangle rectangle() {
    return rectangle;
  }

  @Override
  public Point bottomRight() {
    return rectangle.bottomRight();
  }

  @Override
  public void render(CharGrid grid) {
    grid.put(rectangle.topLeft(), '+')
            .put(rectangle.topRight(), '+')
            .put(rectangle.bottomLeft(), '+')
            .put(rectangle.bottomRight(), '+');

    grid.putHorizontalLine(rectangle.topLeft().right(1), rectangle.rightColumn());
    grid.putHorizontalLine(rectangle.bottomLeft().right(1), rectangle.rightColumn());

    grid.putVerticalLine(rectangle.topLeft().down(1), rectangle.bottomRow());
    grid.putVerticalLine(rectangle.topRight().down(1), rectangle.bottomRow());

    PairStream.zipWithIndex(renderVertex()).forEach((line, row) -> {
      grid.putString(rectangle.topLeft().translate(1, row.intValue()), line);
    });
  }

  @Override
  public void visit(Visitor<T> visitor) {
    visitor.visit(this);
  }

  @Override
  public boolean canRemoveRow(int row) {
    return !rectangle.containsRow(row);
  }

  @Override
  public void removeRows(int firstRow, int count) {
    if (rectangle.topRow() > firstRow) {
      rectangle = rectangle.up(count);
      assert rectangle.topRow() > firstRow;
    }
  }

  private Stream<String> renderVertex() {
    Dimension allocated = rectangle.dimension();
    Stream<String> paddedLines = Arrays.stream(vertex.label())
            .limit(allocated.height())
            .map(line -> Strings.repeat(" ", (allocated.width() - line.length()) / 2 - 1) + line);

    int verticalDiscrepancy = Math.max(0, allocated.height() - vertex.label().length);

    if (verticalDiscrepancy < 2) return paddedLines;

    List<String> blankLines = Collections.nCopies(verticalDiscrepancy / 2, "");
    return Streams.concat(
            blankLines.stream(),
            paddedLines,
            blankLines.stream()
    );
  }
}
