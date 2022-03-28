package upstart.util.graphs.render;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.google.common.collect.Streams;
import com.google.common.graph.EndpointPair;
import com.google.common.graph.Graph;
import com.google.common.graph.GraphBuilder;
import com.google.common.graph.Graphs;
import com.google.common.graph.ImmutableGraph;
import com.google.common.graph.MutableGraph;
import upstart.util.collect.MoreStreams;
import upstart.util.collect.PairStream;
import upstart.util.geometry.Dimension;
import upstart.util.geometry.Point;
import upstart.util.geometry.Rectangle;
import org.immutables.value.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.IntUnaryOperator;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * Given a DAG of items with vertices of type {@link T}, and a function for formatting those vertices, produces an
 * ascii-string rendering of the vertices and edges in the graph.
 *
 * Loosely ported from a scala implementation found here: https://github.com/mdr/ascii-graphs
 */
public class GraphRenderer<T> {
  private static final Logger LOG = LoggerFactory.getLogger(GraphRenderer.class);
  private final GraphSkeleton<T> skeleton;

  /*
   * TODO: make various things configurable:
   *  - customizable graphical rendering characters (eg, support unicode)
   *  - render horizontally vs. vertically (perhaps using Grid.transpose, but requires different handling for labels)
   */
  public GraphRenderer(ImmutableGraph<T> graph) {
    checkArgument(!graph.nodes().isEmpty(), "Graph was empty");
    checkArgument(!Graphs.hasCycle(graph), "Graph had a cycle");
    skeleton = buildSkeleton(graph);
  }

  public String render(Function<? super T, String> vertexRenderer) {
    GraphDrawings<T> drawings = buildDrawings(vertexRenderer);

    Point bottomRight = drawings.computeBottomRight();

    CharGrid grid = new CharGrid(Dimension.of(bottomRight.x() + 1, bottomRight.y() + 1));

    drawings.allDrawings().forEach(drawing -> {
      drawing.render(grid);
      if (LOG.isDebugEnabled()) LOG.debug("Partial:\n{}", grid.render());
    });

    return "\n" + grid.render();
  }

  public GraphDrawings<T> buildDrawings(Function<? super T, String> vertexRenderer) {
    GraphLayout<T> graphLayout = GraphLayout.buildLayout(skeleton, vertexRenderer);
    List<LayerLayout<T>> layerLayouts = Streams.mapWithIndex(skeleton.layers().stream(),
            (layer, i) -> new LayerLayout<>(layer, (int) i, graphLayout)
    ).collect(Collectors.toList());

    int maxLayerWidth = layerLayouts.stream().mapToInt(LayerLayout::width).max().getAsInt();
    for (LayerLayout<T> layerLayout : layerLayouts) {
      layerLayout.spaceVertices(maxLayerWidth);
    }

    LayerLayout<T> prevLayerLayout = layerLayouts.get(0);
    for (LayerLayout<T> layerLayout : layerLayouts.subList(1, layerLayouts.size())) {
      layerLayout.nudgePorts(prevLayerLayout);

      prevLayerLayout = layerLayout;
    }

    Stream<Drawing<T>> drawings = MoreStreams.scan(LayerDrawingState.start(graphLayout),
            layerLayouts.stream(),
            this::renderLayerElements
    ).map(LayerDrawingState::drawings)
            .flatMap(Collection::stream);

    GraphDrawings<T> model = new GraphDrawings<>(drawings);

    model.removeKinks();

    model.elevateEdges();

    model.removeRedundantRows();

    return model;
  }

  private LayerDrawingState<T> renderLayerElements(LayerDrawingState<T> prevLayer, LayerLayout<T> layerLayout) {
    List<EdgeLayout<T>> edgeLayouts = layerLayout.buildEdgeLayouts(prevLayer);

    Map<EdgeLayout<T>, Integer> edgeBendRows = edgeBendRows(edgeLayouts);

    int prevVertexZoneEnd = prevLayer.vertexZoneBottom();
    IntUnaryOperator bendRow = row -> prevVertexZoneEnd + row + 2;

    int downShift = (edgeLayouts.isEmpty()
            ? prevVertexZoneEnd
            : edgeBendRows.values().stream()
                    .max(Comparator.naturalOrder())
                    .map(bendRow::applyAsInt)
                    .orElse(prevVertexZoneEnd)
    ) + 3;

    Map<EdgeLayout<T>, List<Point>> edgePoints = PairStream.withMappedValues(edgeLayouts.stream(),
            edgeLayout -> {
              Point trueEnd = edgeLayout.end().down(downShift);
              List<Point> priorPoints = edgeLayout.edge().source().visit(
                      bend -> Lists.newArrayList(prevLayer.incompleteEdges().get(bend)),
                      vertex -> Lists.newArrayList(edgeLayout.start())
              );

              Point lastPriorPoint = Iterables.getLast(priorPoints);
              if (lastPriorPoint.x() != trueEnd.x()) {
                assert edgeLayout.requiresBend() : "Should need a bend";
                int bend = bendRow.applyAsInt(edgeBendRows.get(edgeLayout));
                assert bend > lastPriorPoint.y() && bend < trueEnd.y();
                priorPoints.add(lastPriorPoint.withY(bend));
                priorPoints.add(trueEnd.withY(bend));
              }
              priorPoints.add(trueEnd);
              removeRedundantPoints(priorPoints);
              return priorPoints;
            }).toMap();

    ImmutableMap<Bend<T>, List<Point>> incompleteEdges = PairStream.of(edgePoints)
            .mapKeys(edge -> edge.edge().target())
            .<Bend<T>>filterKeys(Bend.class)
            .toImmutableMap();

    List<VertexDrawing<T>> vertexDrawings = PairStream.of(layerLayout.vertexLayouts)
            .<RealVertex<T>>filterKeys(RealVertex.class)
            .mapKeys(prevLayer.graphLayout().vertexBoxes()::get)
            .mapValues(layout -> layout.rectangle().down(downShift))
            .map(VertexDrawing<T>::new)
            .collect(Collectors.toList());

    Stream<EdgeDrawing<T>> edgeDrawings = PairStream.of(edgePoints)
            .filterKeys(edgeLayout -> edgeLayout.edge().target().isRealVertex())
            .values()
            .map(EdgeDrawing::new);

    ImmutableMap<EndpointPair<Vertex<T>>, Point> outPorts = PairStream.of(layerLayout.vertexLayouts.values()
            .stream()
            .flatMap(vertexLayout -> vertexLayout.outPorts().entrySet().stream()))
            .mapValues(point -> point.down(downShift))
            .toImmutableMap();

    int vertexBottom = vertexDrawings.stream()
            .mapToInt(vertexDrawing -> vertexDrawing.rectangle().bottomRow())
            .max()
            .getAsInt();

    return LayerDrawingState.<T>builder()
            .addAllDrawings(vertexDrawings)
            .addAllDrawings((Iterable<EdgeDrawing<T>>) edgeDrawings::iterator)
            .outPorts(outPorts)
            .vertexZoneBottom(vertexBottom)
            .incompleteEdges(incompleteEdges)
            .graphLayout(prevLayer.graphLayout())
            .build();
  }


  static void removeRedundantPoints(List<Point> points) {
    if (points.get(0).equals(points.get(1))) points.remove(1);

    for (int i = 0; i < points.size() - 2; ) {
      int i2 = i + 1;
      int i3 = i + 2;
      Point p1 = points.get(i);
      Point p2 = points.get(i2);
      Point p3 = points.get(i3);

      if (p2.equals(p3)) {
        points.remove(i3);
      } else if (Point.colinear(p1, p2, p3)) {
        points.remove(i2);
      } else {
        i++;
      }
    }
  }

  private static <T> Map<EdgeLayout<T>, Integer> edgeBendRows(List<EdgeLayout<T>> edgeLayouts) {
    if (edgeLayouts.isEmpty()) return ImmutableMap.of();

    Map<EdgeLayout<T>, Integer> edgeToRow = PairStream.zipWithIndex(
            edgeLayouts.stream()
                    .filter(EdgeLayout::requiresBend)
                    .sorted(Comparator.comparingInt(EdgeLayout::edgeRank))
    ).mapValues(Long::intValue)
            .toMap();

    List<EdgeLayout<T>> bentEdges = new ArrayList<>(edgeToRow.keySet());

    Multimap<EdgeLayout<T>, EdgeLayout<T>> swappedPairs = ArrayListMultimap.create();

    boolean swapped;
    do {
      swapped = false;
      for (EdgeLayout<T> edge1 : bentEdges) {
        for (EdgeLayout<T> edge2 : bentEdges) {
          if (edge1 == edge2) continue;
          Point start1 = edge1.start();
          Point end1 = edge1.end();
          Point start2 = edge2.start();
          Point end2 = edge2.end();
          if (start1.x() != end2.x()
                  || end1.x() == start2.x() // prevents an infinite loop
                  || swappedPairs.containsEntry(edge1, edge2)
          ) {
            continue;
          }
          int row1 = edgeToRow.get(edge1);
          int row2 = edgeToRow.get(edge2);
          if (row1 <= row2) continue;
          edgeToRow.put(edge1, row2);
          edgeToRow.put(edge2, row1);
          swappedPairs.put(edge1, edge2); // prevent more infinite loops
          swapped = true;
        }
      }
    } while (swapped);
    return edgeToRow;
  }

  private static <T> Map<T, Integer> longestDistancesToSink(Graph<T> graph) {
    Set<T> finalizedVertices = graph.nodes().stream().filter(node -> graph.outDegree(node) == 0).collect(Collectors.toSet());
    Set<T> boundary = Sets.newHashSet(finalizedVertices);
    Map<T, Integer> distances = graph.nodes().stream().collect(Collectors.toMap(Function.identity(), ignored -> 0));

    while (!boundary.isEmpty()) {
      Set<T> newBoundary = new HashSet<>();
      for (T boundaryVertex : boundary) {
        int boundaryDistance = distances.get(boundaryVertex);
        inVertices(boundaryVertex, graph).forEach(v1 -> {
          int v1Dist = distances.get(v1);
          if (v1Dist <= boundaryDistance) distances.put(v1, boundaryDistance + 1);
          if (finalizedVertices.containsAll(graph.successors(v1))) {
            finalizedVertices.add(v1);
            newBoundary.add(v1);
          }
        });
      }
      boundary = newBoundary;
    }
    return distances;
  }

  private static <T> Stream<T> inVertices(T node, Graph<T> graph) {
    return graph.predecessors(node).stream();
  }

  private GraphSkeleton<T> buildSkeleton(Graph<T> graph) {
    MutableGraph<Vertex<T>> graphWithBends = GraphBuilder.directed().allowsSelfLoops(false).build();

    Map<T, RealVertex<T>> realVertices = PairStream.withMappedValues(graph.nodes().stream(), node -> {
      RealVertex<T> vertex = new RealVertex<>(node);
      graphWithBends.addNode(vertex);
      return vertex;
    }).toMap();

    Map<T, Integer> distancesToSink = longestDistancesToSink(graph);

    int maxLayer = distancesToSink.values().stream().mapToInt(Integer::intValue).max().getAsInt();

    List<List<Vertex<T>>> layers = new ArrayList<>(maxLayer + 1);
    for (int i = 0; i <= maxLayer; i++) {
      layers.add(new ArrayList<>());
    }

    distancesToSink.forEach((v, d) -> layers.get(d).add(realVertices.get(v)));

    Collections.reverse(layers);

    ToIntFunction<T> computeLayer = node -> maxLayer - distancesToSink.get(node);

    // for each edge in the input-graph, build a chain of vertices from the source-layer to the target-layer,
    // placing dummy Bend-vertices into each intervening layer to represent the path of the edge connecting the real
    // vertices
    for (EndpointPair<T> edge : graph.edges()) {
      int fromLayer = computeLayer.applyAsInt(edge.source());
      int toLayer = computeLayer.applyAsInt(edge.target());
      assert toLayer > fromLayer : "layers misaligned: " + fromLayer + ", " + toLayer;
      Vertex<T> prev = realVertices.get(edge.source());
      for (int i = fromLayer + 1; i < toLayer; i++) {
        Bend<T> bend = new Bend<>();
        layers.get(i).add(bend);
        graphWithBends.putEdge(prev, bend);
        prev = bend;
      }
      graphWithBends.putEdge(prev, realVertices.get(edge.target()));
    }

    // try to eliminate edge-crossing: sort the vertices in each layer by the average index of their connected vertices
    // ("barycenters") in the previous layer
    for (int i = 1; i <= maxLayer; i++) {
      List<Vertex<T>> prevLayer = layers.get(i - 1);
      List<Vertex<T>> layer = layers.get(i);
      Map<Vertex<T>, Double> barycenters = PairStream.withMappedValues(layer.stream(),
              vertex -> inVertices(vertex, graphWithBends).mapToInt(prevLayer::indexOf)
                      .average()
                      .orElse(Double.MAX_VALUE)
      ).toMap();

      layer.sort(Comparator.comparingDouble(barycenters::get));
    }

    return GraphSkeleton.<T>builder()
            .realVertices(new ArrayList<>(realVertices.values()))
            .layers(layers)
            .graphWithBends(graphWithBends)
            .build();
  }

  @Value.Immutable
  interface LayerDrawingState<T> {
    static <V> ImmutableLayerDrawingState.Builder<V> builder() {
      return ImmutableLayerDrawingState.builder();
    }

    static <V> LayerDrawingState<V> start(GraphLayout<V> graphLayout) {
      return LayerDrawingState.<V>builder().vertexZoneBottom(-3).graphLayout(graphLayout).build();
    }

    List<Drawing<T>> drawings();
    Map<EndpointPair<Vertex<T>>, Point> outPorts();

    Map<Bend<T>, List<Point>> incompleteEdges();

    int vertexZoneBottom();
    GraphLayout<T> graphLayout();
  }

  static class GraphDrawings<T> {
    final List<EdgeDrawing<T>> edgeDrawings;
    final List<VertexDrawing<T>> vertexDrawings;
    final Map<Orientation, Set<Rectangle>> occupiedRegions = new EnumMap<>(Orientation.class);

    GraphDrawings(Stream<Drawing<T>> drawings) {
      Set<Rectangle> horizontal = new HashSet<>();
      Set<Rectangle> vertical = new HashSet<>();
      occupiedRegions.put(Orientation.Horizontal, horizontal);
      occupiedRegions.put(Orientation.Vertical, vertical);

      ImmutableList.Builder<EdgeDrawing<T>> edgeBuilder = ImmutableList.builder();
      ImmutableList.Builder<VertexDrawing<T>> vertexBuilder = ImmutableList.builder();
      drawings.forEach(d -> d.visit(new Drawing.Visitor<T>() {
        @Override public void visit(VertexDrawing<T> vertexDrawing) {
          vertexBuilder.add(vertexDrawing);
          horizontal.add(vertexDrawing.rectangle());
          vertical.add(vertexDrawing.rectangle());
        }

        @Override public void visit(EdgeDrawing<T> edgeDrawing) {
          edgeBuilder.add(edgeDrawing);
          edgeDrawing.segments().forEach(GraphDrawings.this::addOccupiedSegment);
        }
      }));
      edgeDrawings = edgeBuilder.build();
      vertexDrawings = vertexBuilder.build();
    }

    public Stream<Drawing<T>> allDrawings() {
      return Stream.concat(vertexDrawings.stream(), edgeDrawings.stream());
    }

    public Optional<VertexDrawing<T>> vertexContaining(Point vertexPort) {
      return vertexDrawings.stream().filter(vd -> vd.rectangle().contains(vertexPort))
              .findFirst();
    }

    void addOccupiedSegment(EdgeSegment segment) {
      occupiedRegions.get(segment.orientation()).add(segment.rectangle());
    }

    void removeOccupiedSegment(EdgeSegment segment) {
      occupiedRegions.get(segment.orientation()).remove(segment.rectangle());
    }

    boolean collides(EdgeSegment segment) {
      return collides(segment.orientation(), segment.rectangle());
    }

    boolean collides(EdgeSegment s1, EdgeSegment s2, EdgeSegment s3) {
      return collides(s1) || collides(s2) || collides(s3)
              || (s2.orientation() == Orientation.Horizontal && collides(Orientation.Vertical, s2.rectangle()) && collides(Orientation.Horizontal, s3.rectangle()));
    }

    boolean collides(Orientation orientation, Rectangle rectangle) {
      return occupiedRegions.get(orientation).stream().anyMatch(rectangle::intersects);
    }

    public Point computeBottomRight() {
      return allDrawings()
              .map(Drawing::bottomRight)
              .reduce(Point.ORIGIN, Point::maxRowCol);
    }

    public void elevateEdges() {
      boolean changed;
      do {
        changed = false;
        for (EdgeDrawing<T> edgeDrawing : edgeDrawings) {
          changed |= edgeDrawing.elevateEdges(this);
        }
      } while (changed);
    }

    public void removeKinks() {
      edgeDrawings.forEach(drawing -> drawing.eliminateKinks(this));
    }

    public void removeRedundantRows() {
      int bottomRow = allDrawings().map(Drawing::bottomRight).mapToInt(Point::y).max().getAsInt() - 4;
      int removeCount = 0;
      for (int i = 3; i < bottomRow; i++) {
        if (canRemoveRow(i)) {
          removeCount++;
        } else if (removeCount > 0) {
          removeRows(i - removeCount, removeCount);
          bottomRow -= removeCount;
          i -= removeCount;
          removeCount = 0;
        }
      }
      if (removeCount > 0) {
        removeRows(bottomRow - removeCount, removeCount);
      }
    }

    private boolean canRemoveRow(int row) {
      return allDrawings().allMatch(d -> d.canRemoveRow(row));
    }

    private void removeRows(int firstRow, int count) {
      allDrawings().forEach(d -> d.removeRows(firstRow, count));
    }
  }
}
