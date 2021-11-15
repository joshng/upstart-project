package upstart.util.graphs.render;

import java.util.function.Function;

class RealVertex<T> extends Vertex<T> {
  final T vertex;

  RealVertex(T vertex) {
    this.vertex = vertex;
  }

  @Override
  boolean isRealVertex() {
    return true;
  }

  @Override
  <O> O visit(Function<? super Bend<T>, O> onBend, Function<? super RealVertex<T>, O> onVertex) {
    return onVertex.apply(this);
  }
}

