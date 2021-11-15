package upstart.util.graphs.render;

import com.google.common.graph.EndpointPair;
import upstart.util.geometry.Point;
import org.immutables.value.Value;

@Value.Immutable
interface EdgeLayout<T> {
  static <T> ImmutableEdgeLayout.Builder<T> builder() {
    return ImmutableEdgeLayout.builder();
  }

  EndpointPair<Vertex<T>> edge();

  Point start();

  Point end();

  default boolean isStraight() {
    return start().x() == end().x();
  }

  default boolean requiresBend() {
    return !isStraight();
  }

  // We sort so as to avoid unnecessary crossings of edges in or out of a common vertex:
  //
  //  ╭─────╮                  ╭─────╮
  //  │  a  │                  │  a  │
  //  ╰─┬─┬─╯                  ╰─┬─┬─╯
  //    │ │                      │ │
  //    ╰─┼─╮             vs     │ ╰──────────╮
  //      ╰─┼────────╮           ╰───╮        │
  //        │        │               │        │
  //        v        v               v        v
  //  ╭──────────╮ ╭───╮       ╭──────────╮ ╭───╮
  //  │aaaaaaaaaa│ │ b │       │aaaaaaaaaa│ │ b │
  //  ╰──────────╯ ╰───╯       ╰──────────╯ ╰───╯
  //
  //  ╭──────────╮ ╭───╮       ╭──────────╮ ╭───╮
  //  │aaaaaaaaaa│ │ b │       │aaaaaaaaaa│ │ b │
  //  ╰─────┬────╯ ╰─┬─╯       ╰─────┬────╯ ╰─┬─╯
  //        │        │               │        │
  //      ╭─┼────────╯    vs     ╭───╯        │
  //    ╭─┼─╯                    │ ╭──────────╯
  //    │ │                      │ │
  //    v v                      v v
  //  ╭─────╮                  ╭─────╮
  //  │  a  │                  │  a  │
  //  ╰─────╯                  ╰─────╯

  /**
   * Magic ranking to ensure that the edge bends ordered this way minimize crossings.
   */
  @Value.Derived
  default int edgeRank() {
    return compareNum(start().x(), end().x()) * end().x();
  }

  static int compareNum(int i, int j) {
    // could use Integer.compare(i, j), but the docs don't strictly require that to return -1/1.
    // this is also Math.signum(i - j), but that requires type-casting to/from float.
    //noinspection UseCompareMethod
    return i < j ? -1 : i > j ? 1 : 0;
  }
}
