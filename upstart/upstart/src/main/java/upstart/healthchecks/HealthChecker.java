package upstart.healthchecks;

import com.google.inject.Binder;
import com.google.inject.Key;
import com.google.inject.binder.LinkedBindingBuilder;
import com.google.inject.multibindings.MapBinder;
import upstart.util.collect.Pair;
import upstart.util.collect.PairStream;
import upstart.util.concurrent.CompletableFutures;
import upstart.util.concurrent.OptionalPromise;
import upstart.util.concurrent.Promise;

import javax.inject.Inject;
import java.lang.annotation.Annotation;
import java.util.Map;
import java.util.Optional;

public class HealthChecker {
  private final Map<String, HealthCheck> healthChecks;

  @Inject
  public HealthChecker(Map<String, HealthCheck> healthChecks) {
    this.healthChecks = healthChecks;
  }

  @SafeVarargs
  public static void bindHealthChecks(Binder binder, Class<? extends HealthCheck>... healthCheckClasses) {
    MapBinder<String, HealthCheck> mapBinder = healthCheckMapBinder(binder);
    for (Class<? extends HealthCheck> healthCheckClass : healthCheckClasses) {
      mapBinder.addBinding(healthCheckClass.getSimpleName()).to(healthCheckClass);
    }
  }
  @SafeVarargs
  public static void bindHealthChecks(Binder binder, Key<? extends HealthCheck>... healthCheckClasses) {
    MapBinder<String, HealthCheck> mapBinder = healthCheckMapBinder(binder);
    for (Key<? extends HealthCheck> healthCheckClass : healthCheckClasses) {
      String name = healthCheckClass.getTypeLiteral().getRawType().getSimpleName();
      Class<? extends Annotation> annotationType = healthCheckClass.getAnnotationType();
      if (annotationType != null) {
        Annotation annotation = healthCheckClass.getAnnotation();
        String annoDescription = annotation != null ? annotation.toString() : annotationType.getSimpleName();
        name += "[" + annoDescription + "]";
      }
      mapBinder.addBinding(name).to(healthCheckClass);
    }
  }

  public static LinkedBindingBuilder<HealthCheck> bindHealthCheck(Binder binder, String name) {
    return healthCheckMapBinder(binder).addBinding(name);
  }

  public static MapBinder<String, HealthCheck> healthCheckMapBinder(Binder binder) {
    return MapBinder.newMapBinder(binder, String.class, HealthCheck.class);
  }

  /**
   * @return a {@link Promise} that will be completed with a {@link HealthCheckFailedException} if any of the health checks fail.
   */
  public Promise<Void> healthyPromise() {
    return checkHealth().thenAccept(HealthCheckFailedException::throwIfUnhealthy);
  }

  public Promise<Map<String, HealthCheck.Unhealthy>> checkHealth() {
    return CompletableFutures.allAsList(
            PairStream.of(healthChecks).map(this::runHealthCheck)
    ).thenApply(results -> PairStream.of(results.stream().flatMap(Optional::stream)).toImmutableMap());
  }

  private OptionalPromise<Pair<String, HealthCheck.Unhealthy>> runHealthCheck(String name, HealthCheck healthCheck) {
    return Promise.of(healthCheck.checkHealth())
            .exceptionally(HealthCheck.HealthStatus::unhealthy)
            .thenFilterOptional(HealthCheck.Unhealthy.class)
            .thenMap(unhealthy -> Pair.of(name, unhealthy));
  }
}
