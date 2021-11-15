package upstart.util.graphs.render;

import com.google.common.graph.EndpointPair;
import upstart.util.PairStream;
import upstart.util.Tuple;
import upstart.util.geometry.Point;
import upstart.util.geometry.Rectangle;
import upstart.util.geometry.Translatable;
import org.immutables.value.Value;

import java.util.Map;

@Value.Immutable
@Tuple
interface VertexLayout<T> extends Translatable<VertexLayout<T>> {
  static <T> VertexLayout<T> of(Rectangle rectangle, Map<EndpointPair<Vertex<T>>, Point> inPorts, Map<EndpointPair<Vertex<T>>, Point> outPorts) {
    return ImmutableVertexLayout.of(rectangle, inPorts, outPorts);
  }

  Rectangle rectangle();

  Map<EndpointPair<Vertex<T>>, Point> inPorts();

  Map<EndpointPair<Vertex<T>>, Point> outPorts();

  default int width() {
    return rectangle().width();
  }

  default int height() {
    return rectangle().height();
  }

  @Override
  default VertexLayout<T> translate(int x, int y) {
    return of(rectangle().translate(x, y),
            PairStream.of(inPorts()).mapValues(p -> p.translate(x, y)).toImmutableMap(),
            PairStream.of(outPorts()).mapValues(p -> p.translate(x, y)).toImmutableMap()
    );
  }
}
