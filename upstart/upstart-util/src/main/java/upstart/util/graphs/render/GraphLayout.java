package upstart.util.graphs.render;

import upstart.util.PairStream;
import upstart.util.geometry.Dimension;
import org.immutables.value.Value;

import java.util.Map;
import java.util.function.Function;


@Value.Immutable
interface GraphLayout<T> {
  static <T> ImmutableGraphLayout.Builder<T> builder() {
    return ImmutableGraphLayout.builder();
  }

  static <T> GraphLayout<T> buildLayout(GraphSkeleton<T> skeleton, Function<? super T, String> vertexRenderer) {
    return GraphLayout.<T>builder()
            .skeleton(skeleton)
            .vertexBoxes(PairStream.withMappedValues(skeleton.realVertices().stream(),
                    vertex -> VertexBox.of(vertex, vertexRenderer.apply(vertex.vertex).split("\r?\n"), skeleton.graphWithBends())
            ).toImmutableMap())
            .build();
  }

  GraphSkeleton<T> skeleton();
  Map<RealVertex<T>, VertexBox<T>> vertexBoxes();

  default Dimension getDimension(Vertex<T> vertex) {
    return vertex.visit(
            bend -> Bend.BEND_DIMENSION,
            realVertex -> vertexBoxes().get(realVertex).dimension()
    );
  }
}
