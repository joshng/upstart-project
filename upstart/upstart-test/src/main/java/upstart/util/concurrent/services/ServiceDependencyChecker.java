package upstart.util.concurrent.services;

import com.google.common.truth.Truth;
import com.google.common.util.concurrent.Service;
import com.google.inject.Injector;
import com.google.inject.Key;
import upstart.managedservices.ManagedServiceGraph;
import upstart.managedservices.ServiceLifecycle;

import javax.inject.Inject;

/**
 * A test-utility for confirming that services are configured with the expected dependency-relationships.
 * <p/>
 * Example:
 * <pre>{@code
 * @UpstartServiceTest
 * class MyApplicationTest extends UpstartModule {
 *
 *   @Inject ServiceDependencyChecker dependencyChecker;
 *
 *   public void configure() {
 *     install(MyApplicationModule.class);
 *   }
 *
 *   @Test
 *   void checkDependencies() {
 *     dependencyChecker.assertThat(Service1.class).dependsUpon(Service2.class);
 *   }
 * }
 * }</pre>
 */
public class ServiceDependencyChecker {
  private final ManagedServiceGraph infrastructureGraph;
  private final ManagedServiceGraph applicationGraph;
  private final Injector injector;

  @Inject
  public ServiceDependencyChecker(
          @ServiceLifecycle(ServiceLifecycle.Phase.Infrastructure) ManagedServiceGraph infrastructureGraph,
          ManagedServiceGraph applicationGraph,
          Injector injector
  ) {
    this.infrastructureGraph = infrastructureGraph;
    this.applicationGraph = applicationGraph;
    this.injector = injector;
  }

  public OngoingDependencyAssertion assertThat(Class<? extends Service> dependentServiceKey) {
    return assertThat(injector.getInstance(dependentServiceKey));
  }

  public OngoingDependencyAssertion assertThat(Key<? extends Service> dependentServiceKey) {
    return assertThat(injector.getInstance(dependentServiceKey));
  }

  public OngoingDependencyAssertion assertThat(Service dependentService) {
    return new OngoingDependencyAssertion(dependentService);
  }

  public class OngoingDependencyAssertion {
    private final Service dependentService;

    OngoingDependencyAssertion(Service dependentService) {
      this.dependentService = dependentService;
    }

    public void dependsUpon(Class<? extends Service> dependency) {
      dependsUpon(injector.getInstance(dependency));
    }

    public void dependsUpon(Key<? extends Service> dependency) {
      dependsUpon(injector.getInstance(dependency));
    }

    public void dependsUpon(Service dependency) {
      Service effectiveDependentService;
      ManagedServiceGraph graph;
      if (infrastructureGraph.contains(dependency)) {
        graph = infrastructureGraph;
        if (applicationGraph.contains(dependentService)) {
          // dependencies from app-services onto infrastructure are really modeled as from the applicationGraph
          effectiveDependentService = applicationGraph;
        } else {
          effectiveDependentService = dependentService;
        }
      } else {
        graph = applicationGraph;
        effectiveDependentService = dependentService;
      }

      Truth.assertWithMessage("Dependencies of %s", dependentService)
              .that(graph.getRequiredDependencies(effectiveDependentService))
              .contains(dependency);
    }
  }
}
