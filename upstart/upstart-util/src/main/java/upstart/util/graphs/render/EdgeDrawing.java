package upstart.util.graphs.render;

import upstart.util.Optionals;
import upstart.util.PairStream;
import upstart.util.geometry.Point;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

class EdgeDrawing<T> implements Drawing<T> {
  private final List<Point> bendPoints;

  EdgeDrawing(List<Point> bendPoints) {
    this.bendPoints = bendPoints;
  }

  @Override
  public Point bottomRight() {
    return bendPoints.stream().reduce(Point.ORIGIN, Point::maxRowCol);
  }

  public List<EdgeSegment> segments() {
    return PairStream.zip(bendPoints.stream(), bendPoints.stream().skip(1))
            .map(EdgeSegment::of)
            .collect(Collectors.toList());
  }

  @Override
  public void render(CharGrid grid) {
    Point prev = null;
    boolean first = true;
    for (Point bendPoint : bendPoints) {
      if (prev != null) {
        if (Orientation.between(prev, bendPoint) == Orientation.Vertical) {
          grid.putVerticalLine(prev.down(1), bendPoint.y());
        } else {
          grid.putHorizontalLine(prev.minRowCol(bendPoint).right(1), prev.maxRowCol(bendPoint).x());
        }
        grid.put(prev, first ? '|' : 'o');
        first = false;
      }
      prev = bendPoint;
    }
    assert prev != null;
    grid.put(prev, 'V');
  }


  @Override
  public boolean canRemoveRow(int row) {
    Point lastBend = bendPoints.get(bendPoints.size() - 1);

    if (lastBend.y() == row + 1) return false;
    return bendPoints.stream().mapToInt(Point::y).noneMatch(n -> n == row);
  }

  @Override
  public void removeRows(int startRow, int count) {
    for (int i = 0; i < bendPoints.size(); i++) {
      Point point = bendPoints.get(i);
      if (point.y() > startRow) {
        bendPoints.set(i, point.up(count));
      }
    }
  }

  void eliminateKinks(GraphRenderer.GraphDrawings<T> edgeTracker) {
    new KinkEliminator(edgeTracker).eliminateKinks();
  }

  boolean elevateEdges(GraphRenderer.GraphDrawings<T> edgeTracker) {
    boolean changed = false;
    List<EdgeSegment> segments = segments();
    segments.forEach(edgeTracker::removeOccupiedSegment);

    for (int i = 1; i < bendPoints.size() - 1; i += 2) {
      EdgeSegment s1 = segments.get(i - 1);
      EdgeSegment s2 = segments.get(i);
      EdgeSegment s3 = segments.get(i + 1);
      assert s2.orientation() == Orientation.Horizontal; // odd-indexed segments are always horizontal

      boolean elevated = false;
      for (int row = s1.start().y() + 1; !elevated && row < s2.start().y(); row++) {
        EdgeSegment newS1 = s1.withEndRow(row);
        EdgeSegment newS2 = s2.withRow(row);
        EdgeSegment newS3 = s3.withStartRow(row);

        elevated = !edgeTracker.collides(newS1, newS2, newS3);
        if (elevated) {
          assert i == bendPoints.indexOf(s2.start());
          bendPoints.set(i, newS2.start());
          bendPoints.set(i + 1, newS2.end());
          segments = segments();
          changed = true;
        }
      }
    }

    segments.forEach(edgeTracker::addOccupiedSegment);
    return changed;
  }

  @SuppressWarnings("OptionalGetWithoutIsPresent")
  class KinkEliminator {
    private final GraphRenderer.GraphDrawings<T> drawings;
    private List<EdgeSegment> segments = segments();
    int segmentIdx;

    KinkEliminator(GraphRenderer.GraphDrawings<T> drawings) {
      this.drawings = drawings;
    }

    private void reset() {
      segmentIdx = 0;
      segments = segments();
    }

    void eliminateKinks() {
      if (bendPoints.size() < 4) return;

      reset();

      segments.forEach(drawings::removeOccupiedSegment);

      while (segmentIdx < segments.size() - 1) {
        Optional<Point> simplifiedMiddlePoint = findSimplerBendPoint();
        Optionals.ifPresentOrElse(simplifiedMiddlePoint,
                newMiddlePoint -> {
                  // replace the 3 points starting at segmentIdx with this new single middle-point
                  bendPoints.set(segmentIdx, newMiddlePoint);
                  bendPoints.subList(segmentIdx + 1, segmentIdx + 3).clear();
                  GraphRenderer.removeRedundantPoints(bendPoints);

                  // we changed the edge, so start over to find further simplifications
                  reset();
                },
                () -> segmentIdx++); // no change, proceed to next segment
      }

      segments.forEach(drawings::addOccupiedSegment);
    }

    // edge-simplification diagrams:
    // when segment2.orientation == Vertical:
    //
    //   segment1
    // ...──start─@.............* alternativeMiddle
    //            │             .
    //   segment2 │             .
    //            │             .
    //     middle ╰─────────────@ end
    //               segment3   │
    //                          │ segment4
    //                          │
    //                          .
    //                          .
    /////////////////////////////////////////////////////////
    //
    // when segment2.orientation == Horizontal:
    //                    .
    //                    .
    //                    │
    //           segment1 │
    //                    │  segment2
    //              start @────────────╮ middle
    //                    .            │
    //                    .            │ segment3
    //                    .            │
    //  alternativeMiddle *............@─end───────────...
    //                                      segment4
    //
    // for both orientations: consider whether we can replace the start, middle, and end points with just the
    // alternativeMiddle point, without colliding with other elements of the graph, or breaking a connection to
    // an associated vertex
    Optional<Point> findSimplerBendPoint() {
      Optional<EdgeSegment> segment1 = Optionals.getWithinBounds(segments, segmentIdx - 1);
      EdgeSegment segment2 = segments.get(segmentIdx);
      EdgeSegment segment3 = segments.get(segmentIdx + 1);
      Optional<EdgeSegment> segment4 = Optionals.getWithinBounds(segments, segmentIdx + 2);

      Point start = segment2.start();
      Point end = segment3.end();

      Orientation orientation = segment2.orientation();
      Point alternativeMiddle = orientation == Orientation.Vertical
              ? Point.of(end.x(), start.y())
              : Point.of(start.x(), end.y());

      Optional<EdgeSegment> newSegment1 = segment1.map(s1 -> s1.withEnd(alternativeMiddle));
      Optional<EdgeSegment> newSegment4 = segment4.map(s4 -> s4.withStart(alternativeMiddle));

      boolean rejected = newSegment1.map(drawings::collides).orElseGet(() -> {
        // we have no segment1, so start must be below a vertex; ensure that alternativeMiddle is also below the vertex
        assert orientation == Orientation.Vertical;
        VertexDrawing<T> vertex = drawings.vertexContaining(start.up(1)).get();
        return alternativeMiddle.x() <= vertex.rectangle().leftColumn() || alternativeMiddle.x() >= vertex.rectangle().rightColumn();
      }) || newSegment4.map(drawings::collides).orElseGet(() -> {
        // we have no segment4, so end must be above a vertex; ensure that alternativeMiddle is also above the vertex
        assert orientation == Orientation.Horizontal;
        VertexDrawing<T> vertex = drawings.vertexContaining(end.down(1)).get();
        return alternativeMiddle.x() <= vertex.rectangle().leftColumn() || alternativeMiddle.x() >= vertex.rectangle().rightColumn();
      });

      return Optionals.onlyIf(!rejected, alternativeMiddle);
    }
  }

  @Override
  public void visit(Visitor<T> visitor) {
    visitor.visit(this);
  }
}
