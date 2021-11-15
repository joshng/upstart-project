package upstart.util.graphs.render;

import com.google.common.graph.Graph;
import upstart.util.geometry.Dimension;
import org.immutables.value.Value;

import java.util.stream.Stream;

/**
 * The size and contents of the box to render for a given vertex ({@link T}). Recomputed each {@link GraphRenderer#render},
 * in case the label for the vertex has changed.
 * <p/>
 * (If the labels of the vertices are immutable, then the result of calling {@link GraphRenderer#render} once could be
 * cached externally.)
 */
@Value.Immutable
interface VertexBox<T> {
  static <T> ImmutableVertexBox.Builder<T> builder() {
    return ImmutableVertexBox.builder();
  }

  static <T> VertexBox<T> of(RealVertex<T> vertex, String[] labelLines, Graph<Vertex<T>> graphWithBends) {
    int labelWidth = Stream.of(labelLines).mapToInt(String::length).max().orElse(0);

    int inDegree = graphWithBends.inDegree(vertex);
    int outDegree = graphWithBends.outDegree(vertex);

    int minWidth = Math.max(inDegree, outDegree) * 2 + 3;
    int width = Math.max(minWidth, labelWidth + 2);
    int height = Math.max(3, labelLines.length + 2);
    Dimension dimension = Dimension.of(width, height);

    return VertexBox.<T>builder()
            .vertex(vertex)
            .label(labelLines)
            .dimension(dimension)
            .build();
  }

  RealVertex<T> vertex();

  String[] label();

  Dimension dimension();

}
