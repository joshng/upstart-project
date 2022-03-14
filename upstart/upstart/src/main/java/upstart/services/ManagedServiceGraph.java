package upstart.services;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import com.google.common.graph.Graph;
import com.google.common.graph.GraphBuilder;
import com.google.common.graph.Graphs;
import com.google.common.graph.ImmutableGraph;
import com.google.common.graph.MutableGraph;
import com.google.common.util.concurrent.AbstractService;
import com.google.common.util.concurrent.Service;
import upstart.util.MoreStreams;
import upstart.util.Pair;
import upstart.util.PersistentList;
import upstart.util.concurrent.LazyReference;
import upstart.util.graphs.render.GraphRenderer;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public class ManagedServiceGraph extends AggregateService {
  private final ImmutableMap<Service, LifecycleCoordinator> dependencies;
  private final Graph<Service> graph;
  private final LazyReference<GraphRenderer<Service>> graphRenderer;

  public ManagedServiceGraph(
          Iterable<? extends Service> allServices,
          Iterable<? extends Map.Entry<? extends Service, ? extends Service>> dependencies
  ) {
    this(buildMap(allServices, dependencies));
  }

  public ManagedServiceGraph(Service dependent, Service requirement) {
    this(Arrays.asList(dependent, requirement), Collections.singleton(Pair.of(dependent, requirement)));
  }

  private ManagedServiceGraph(ImmutableMap<Service, LifecycleCoordinator> dependencies) {
    this.dependencies = dependencies;
    Set<LifecycleCoordinator> validated = Sets.newHashSet();
    for (LifecycleCoordinator service : dependencies.values()) {
      checkForCycles(PersistentList.of(service), validated);
    }
    graph = buildGraph(dependencies.values());
    graphRenderer = LazyReference.from(() -> new GraphRenderer<>(ImmutableGraph.copyOf(Graphs.transpose(graph))));
  }

  public ImmutableSet<Service> getAllServices() {
    return dependencies.keySet();
  }

  public boolean contains(Service service) {
    return dependencies.containsKey(service);
  }

  public Iterable<Service> getRequiredDependencies(Service dependentService) {
    return graph.successors(dependentService);
  }

  public <S extends Service> S getService(Class<S> serviceClass) {
    return MoreStreams.filter(dependencies.keySet().stream(), serviceClass)
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Service not found: " + serviceClass.getName()));
  }

  @Override protected Iterable<? extends ComposableService> getComponentServices() {
    return dependencies.values();
  }

  private void checkForCycles(PersistentList<LifecycleCoordinator> context, Set<LifecycleCoordinator> validated) {
    for (LifecycleCoordinator requirement : context.head().getRequiredServices()) {
      if (!validated.contains(requirement)) {
        PersistentList<LifecycleCoordinator> nestedContext = context.with(requirement);
        if (context.contains(requirement)) {
          throw new IllegalArgumentException(
                  "Cyclic dependency!! Consider refactoring to isolate responsibilities:\n  "
                          + Joiner.on(" -> ")
                                  .join(Iterables.transform(nestedContext.reversed(), LifecycleCoordinator::getUnderlyingService)));
        }
        checkForCycles(nestedContext, validated);
        validated.add(requirement);
      }
    }
  }

  public static ManagedServiceGraph buildGraph(
          Iterable<? extends Service> allServices,
          Iterable<? extends Map.Entry<? extends Service, ? extends Service>> dependencies
  ) {
    return new ManagedServiceGraph(allServices, dependencies);
  }

  private static ImmutableMap<Service, LifecycleCoordinator> buildMap(
          Iterable<? extends Service> allServices,
          Iterable<? extends Map.Entry<? extends Service, ? extends Service>> dependencies
  ) {
    Map<Service, LifecycleCoordinator> wrappers = new HashMap<>();

    Function<Service, LifecycleCoordinator> wrap =
            service -> wrappers.computeIfAbsent(service, LifecycleCoordinator::new);

    for (Service service : allServices) {
      wrap.apply(service); // ensure the root services are wrapped
    }

    for (Map.Entry<? extends Service, ? extends Service> dependency : dependencies) {
      Service dependent = dependency.getKey();
      Service required = dependency.getValue();

      wrap.apply(dependent).addRequiredService(wrap.apply(required));
    }

    return ImmutableMap.copyOf(wrappers);
  }

  public String toString() {
    return renderGraph(Object::toString);
  }

  public String renderGraph(Function<? super Service, String> serviceRenderer) {
    return graph.nodes().isEmpty() ? "[no services]" : graphRenderer.get().render(serviceRenderer);
  }

  private static Graph<Service> buildGraph(Collection<LifecycleCoordinator> services) {
    MutableGraph<Service> graph = GraphBuilder.directed().allowsSelfLoops(false).expectedNodeCount(services.size()).build();
    for (LifecycleCoordinator serviceWithDeps : services) {
      Service service = serviceWithDeps.getUnderlyingService();
      graph.addNode(service);
      for (LifecycleCoordinator requirement : serviceWithDeps.getRequiredServices()) {
          graph.putEdge(service, requirement.getUnderlyingService());
      }
    }

    // empty graphs are problematic, so include a dummy service
    if (graph.nodes().isEmpty()) graph.addNode(new NoOpService());
    return graph;
  }

  private static class NoOpService extends AbstractService {
    @Override
    protected void doStart() {
      notifyStarted();
    }

    @Override
    protected void doStop() {
      notifyStopped();
    }

    @Override
    public String toString() {
      return "[no services]";
    }
  }
}
