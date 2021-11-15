package upstart.metrics;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Reporter;

import javax.inject.Inject;

/**
 * NOTE: to avoid data-loss, metric reporters should shut down AFTER any other managed services that emit metrics
 * via the {@link MetricRegistry}. This means such services effectively DEPEND UPON the reporters.
 * <p/>
 * However, the MetricRegistry is a <em>decoupling</em> mechanism, purposefully designed to eliminate this
 * natural dependency from the object-graph: writers write to the registry, and reporters read from it,
 * but they don't directly refer to one another. Thus, in order for the {@link ManagedServicesModule.ServiceManager}
 * to arrange service lifecycles as desired, we need to expose this hidden dependency to guice.
 * <p/>
 * We achieve this exposure by arranging for the MetricRegistry itself to depend upon the reporters by way of the
 * {@link UpstartMetricsModule#reporterBinder}. This results in Services which {@link Inject} the {@link MetricRegistry}
 * inheriting a transitive dependency on the reporters.
 * <p/>
 * Finally, to avoid circular dependencies (reporters certainly need the registry), we pass the registry into the
 * reporters via this interface.
 */
public interface TaggedMetricReporter extends Reporter {
  void initMetricRegistry(TaggedMetricRegistry registry);
}
