package upstart.util.concurrent.services;

import com.google.common.graph.GraphBuilder;
import com.google.common.graph.ImmutableGraph;
import com.google.common.graph.MutableGraph;
import upstart.util.graphs.render.GraphRenderer;
import org.junit.jupiter.api.Test;

import java.util.BitSet;
import java.util.Random;

class GraphRendererTest {
  @Test
  void renderRandomGraph() {
//    Random random = new Random(1234); // decent seed: applies the nudgePorts behavior
    long seed = System.currentTimeMillis();
    Random random = new Random(seed);
    MutableGraph<Integer> g = GraphBuilder.directed().build();

    int nodeCount = 20;
    for (int i = 1; i < nodeCount; i++) {
      int edgeCount = random.nextInt(i);
      BitSet links = new BitSet(nodeCount);
      for (int edge = 0; edge < edgeCount; edge++) {
        int link;
        boolean unique;
        do {
          link = random.nextInt(i);
          unique = !links.get(link);
          if (unique) links.set(link);
        } while (!unique);
        g.putEdge(i, link);
      }
    }

    // asserting correctness of the textual output would be a big job (but perhaps worthwhile!).
    // could also call buildRenderModel and apply assertions to the pre-rendered representation.
    // but for now, short on time: just exercise the code to ensure it doesn't throw:

//    Stopwatch watch = Stopwatch.createStarted();
    try {
      String rendered = new GraphRenderer<>(ImmutableGraph.copyOf(g)).render(Object::toString);
//    System.out.println("took " + watch);
//    System.out.println(rendered);
//    System.out.println(g);
    } catch (Exception e) {
      throw new RuntimeException("GraphRendererTest exception, random seed was: " + seed, e);
    }
  }
}