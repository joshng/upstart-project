package upstart.util.graphs.render;

import java.util.function.Function;

abstract class Vertex<T> {
  abstract boolean isRealVertex();

  abstract <O> O visit(Function<? super Bend<T>, O> onBend, Function<? super RealVertex<T>, O> onVertex);
}
