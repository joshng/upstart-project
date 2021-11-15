package upstart.b4;

import com.google.common.graph.ImmutableGraph;
import upstart.util.graphs.render.GraphRenderer;

import java.util.function.Function;
import java.util.stream.Stream;

public class TargetInvocationGraph {
  private final ImmutableGraph<TargetInvocation> invocationGraph;
  private final GraphRenderer<TargetInvocation> graphRenderer;

  public TargetInvocationGraph(ImmutableGraph<TargetInvocation> invocationGraph) {
    this.invocationGraph = invocationGraph;
    graphRenderer = new GraphRenderer<>(invocationGraph);
  }

  public String render(Function<? super TargetInvocation, String> vertexRenderer) {
    return graphRenderer.render(vertexRenderer);
  }

  public Stream<TargetInvocation> allInvocations() {
    return invocationGraph.nodes().stream();
  }

  public Stream<TargetInvocation> successors(TargetInvocation dependency) {
    return invocationGraph.successors(dependency).stream();
  }

  public Stream<TargetInvocation> predecessors(TargetInvocation dependency) {
    return invocationGraph.predecessors(dependency).stream();
  }
}
