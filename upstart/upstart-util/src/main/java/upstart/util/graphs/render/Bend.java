package upstart.util.graphs.render;

import upstart.util.geometry.Dimension;

import java.util.function.Function;

/**
 * A dummy {@link Vertex} representing a portion of an edge between two {@link RealVertex real vertices} which
 * traverses an intervening layer in the computed {@link GraphSkeleton}, granting the possibility to visually
 * <em>bend</em> the edge.
 */
class Bend<T> extends Vertex<T> {
  static final Dimension BEND_DIMENSION = Dimension.of(1, 1);

  @Override
  boolean isRealVertex() {
    return false;
  }

  @Override
  <O> O visit(Function<? super Bend<T>, O> onBend, Function<? super RealVertex<T>, O> onVertex) {
    return onBend.apply(this);
  }
}
