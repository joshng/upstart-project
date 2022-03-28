package upstart.util.graphs.render;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.graph.EndpointPair;
import com.google.common.graph.Graph;
import upstart.util.collect.PairStream;
import upstart.util.geometry.Point;
import upstart.util.geometry.Rectangle;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class LayerLayout<T> {
  private final List<Vertex<T>> vertices;
  private final List<EndpointPair<Vertex<T>>> inEdges;
  Map<Vertex<T>, VertexLayout<T>> vertexLayouts = new HashMap<>();

  LayerLayout(List<Vertex<T>> vertices, long layerIdx, GraphLayout<T> layout) {
    this.vertices = vertices;
    int index = (int) layerIdx;
    GraphSkeleton<T> skeleton = layout.skeleton();
    Optional<List<Vertex<T>>> prevLayer = skeleton.layer(index - 1);
    Optional<List<Vertex<T>>> nextLayer = skeleton.layer(index + 1);

    inEdges = prevLayer.map(prev -> vertices.stream()
            .flatMap(v -> inEdges(v, skeleton.graphWithBends()))
            .sorted(Comparator.comparingInt(e -> prev.indexOf(e.source())))
            .collect(Collectors.toList())
    ).orElse(ImmutableList.of());

    List<EndpointPair<Vertex<T>>> outEdges = nextLayer.map(next -> vertices.stream()
            .flatMap(v -> outEdges(v, skeleton.graphWithBends()))
            .sorted(Comparator.comparingInt(e -> next.indexOf(e.target())))
            .collect(Collectors.toList())
    ).orElse(ImmutableList.of());

    Point nextTopLeft = Point.ORIGIN;
    for (Vertex<T> vertex : vertices) {
      Rectangle box = Rectangle.of(nextTopLeft, layout.getDimension(vertex));
      int vertexWidth = box.width();

      List<EndpointPair<Vertex<T>>> in = inEdges.stream()
              .filter(e -> e.target() == vertex)
              .collect(Collectors.toList());
      Map<EndpointPair<Vertex<T>>, Point> inPorts = layoutPorts(in, vertexWidth, box.topLeft());

      List<EndpointPair<Vertex<T>>> out = outEdges.stream()
              .filter(e -> e.source() == vertex)
              .collect(Collectors.toList());
      Map<EndpointPair<Vertex<T>>, Point> outPorts = layoutPorts(out, vertexWidth, box.bottomLeft());

      vertexLayouts.put(vertex, VertexLayout.of(box, inPorts, outPorts));
      nextTopLeft = box.topRight().right(2);
    }
  }

  private static <T> Stream<EndpointPair<T>> inEdges(T node, Graph<T> graph) {
    return graph.incidentEdges(node).stream().filter(e -> e.target() == node);
  }

  private static <T> Stream<EndpointPair<T>> outEdges(T node, Graph<T> graph) {
    return graph.incidentEdges(node).stream().filter(e -> e.source() == node);
  }

  List<EdgeLayout<T>> buildEdgeLayouts(GraphRenderer.LayerDrawingState<T> prevLayer) {
    return inEdges.stream()
            .map(edge -> {
              Point start = prevLayer.outPorts().get(edge).down(1);
              Point end = vertexLayouts.get(edge.target()).inPorts().get(edge).up(1);
              return EdgeLayout.<T>builder()
                      .edge(edge)
                      .start(start)
                      .end(end)
                      .build();
            }).collect(Collectors.toList());
  }

  private Map<EndpointPair<Vertex<T>>, Point> layoutPorts(List<EndpointPair<Vertex<T>>> in, int vertexWidth, Point leftCorner) {
    int inPortCount = in.size();
    int factor = vertexWidth / (inPortCount + 1);
    int centralizer = (vertexWidth - factor * (inPortCount + 1)) / 2;
    return PairStream.zipWithIndex(in.stream())
            .mapValues((edge, idx) -> leftCorner.right((idx.intValue() + 1) * factor + centralizer))
            .toMap();
  }

  int width() {
    return vertexLayouts.values().stream().mapToInt(VertexLayout::width).sum() + vertexLayouts.size() - 1;
  }

  int height() {
    return vertexLayouts.values().stream().mapToInt(VertexLayout::height).max().getAsInt();
  }

  void spaceVertices(int graphWidth) {
    int extraSpace = graphWidth - vertexLayouts.get(Iterables.getLast(vertices)).rectangle().rightColumn();
    int hspace = Math.max(extraSpace / (vertexLayouts.size() + 1), 1);

    int layerHeight = height();

    int leftColumn = hspace;
    for (Vertex<T> vertex : vertices) {
      VertexLayout<T> vertexLayout = vertexLayouts.get(vertex);
      int xShift = leftColumn - vertexLayout.rectangle().leftColumn();
      int yShift = (layerHeight - vertexLayout.height()) / 2;
      vertexLayouts.put(vertex, vertexLayout.translate(xShift, yShift));
      leftColumn += vertexLayout.width() + hspace;
    }
  }

  /**
   * Nudge edge ports to avoid overlapping vertical edge segments.
   * <p>
   * If an edge starts at the same column as another edge finishes, they could be drawn overlapping.
   * This avoids the issue by moving the in port one column away (and we make the assumption that there is space
   * for that).
   * <p>
   * ╭─────╮ ╭─────╮    ╭─────╮ ╭─────╮
   * │  A  │ │  B  │    │  A  │ │  B  │
   * ╰─┬─┬─╯ ╰─┬─┬─╯    ╰─┬─┬─╯ ╰─┬─┬─╯
   * │ │     │ │        │ │     │ │
   * │ ╰─────┼ │   vs   │ ╰─────┼╮│
   * │ ╭─────╯ │        │  ╭────╯││
   * │ │     │ │        │  │     ││
   * v v     v v        v  v     vv
   * ╭─────╮ ╭─────╮    ╭─────╮ ╭─────╮
   * │  X  │ │  Y  │    │  X  │ │  Y  │
   * ╰─────╯ ╰─────╯    ╰─────╯ ╰─────╯
   */
  void nudgePorts(LayerLayout<T> prevLayerLayout) {
    Set<Integer> prevOutEdgeColumns = prevLayerLayout.vertexLayouts.values()
            .stream()
            .flatMap(vl -> vl.outPorts().values().stream())
            .map(Point::x)
            .collect(Collectors.toSet());

    vertexLayouts = PairStream.of(vertexLayouts).mapValues((vertex, vertexLayout) -> {
      Set<Integer> nudgedColumns = new HashSet<>();
      Map<EndpointPair<Vertex<T>>, Point> newInPorts = PairStream.of(vertexLayout.inPorts())
              .mapValues((pair, point) -> {
                int column = point.x();
                if (prevOutEdgeColumns.contains(column)
                        && prevLayerLayout.vertexLayouts.get(pair.source())
                        .outPorts()
                        .get(pair).x() != column
                ) {
                  nudgedColumns.add(column);
                  return point.right(1);
                } else {
                  return point;
                }
              })
              .toImmutableMap();

      if (nudgedColumns.isEmpty()) return vertexLayout;

      // if this vertex is a bend, also need to nudge the outPorts for inPorts we nudged above
      Map<EndpointPair<Vertex<T>>, Point> newOutPorts = vertex.visit(
              bend -> PairStream.of(vertexLayout.outPorts())
                      .mapValues((pair, point) -> nudgedColumns.contains(point.x())
                              ? point.right(1)
                              : point
                      ).toMap(),
              realVertex -> vertexLayout.outPorts()
      );

      return VertexLayout.of(vertexLayout.rectangle(), newInPorts, newOutPorts);
    }).toImmutableMap();
  }
}
