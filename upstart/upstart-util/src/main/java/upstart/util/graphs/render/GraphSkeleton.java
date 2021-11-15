package upstart.util.graphs.render;

import com.google.common.graph.Graph;
import upstart.util.Optionals;
import org.immutables.value.Value;

import java.util.List;
import java.util.Optional;

/**
 * A partially-computed visual arrangement for a graph: vertices assigned to layers, and a
 * {@link #graphWithBends()} containing vertices connected by chains of {@link Bend} placeholders representing edges
 * that traverse layers. However, nothing has been specifically placed; only relative orderings are determined.
 */
@Value.Immutable
abstract class GraphSkeleton<T> {
  static <T> ImmutableGraphSkeleton.Builder<T> builder() {
    return ImmutableGraphSkeleton.builder();
  }

  /**
   * All real vertices from the source-graph, within {@link RealVertex} wrappers
   */
  abstract List<RealVertex<T>> realVertices();

  /**
   * An ordered list of {@link RealVertex} and {@link Bend} instances for each layer in the graph-drawing
   */
  abstract List<List<Vertex<T>>> layers();

  /**
   * A decorated representation of the source graph, containing {@link RealVertex} wrappers for each source-vertex,
   * but with each edge from the source-graph expanded as a linked chain of {@link Bend} instances in each {@link #layer}
   * traversed by the edge.
   */
  abstract Graph<Vertex<T>> graphWithBends();

  Optional<List<Vertex<T>>> layer(int i) {
    return Optionals.getWithinBounds(layers(), i);
  }
}
