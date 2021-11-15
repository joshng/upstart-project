package upstart.metrics;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.Meter;
import com.codahale.metrics.Metric;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.MetricRegistryListener;
import com.codahale.metrics.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Pattern;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * A MetricRegistry that supports tagged metrics. Also supports nesting of other MetricRegistries
 * with name-prefixes, with tracking of subsequent changes to those registries.
 *
 * @see #meter(String, Map)
 * @see #timer(String, Map)
 * @see #register(String, Map, Metric)
 * @see #registerNestedRegistry(String, MetricRegistry)
 */
@Singleton
public class TaggedMetricRegistry extends MetricRegistry {
  private static final Logger LOG = LoggerFactory.getLogger(TaggedMetricRegistry.class);

  private static final Pattern LEGAL_METRIC_NAME = Pattern.compile("^[\\w.-]+(\\[|$)");

  private final ConcurrentMap<MetricRegistry, String> namesByRegistry = new ConcurrentHashMap<>();

  @Inject
  public TaggedMetricRegistry(Set<TaggedMetricReporter> reporters) {
    if (!reporters.isEmpty()) {
      for (TaggedMetricReporter reporter : reporters) {
        reporter.initMetricRegistry(this);
      }
    } else {
      LOG.warn("No MetricReporters were registered with the MetricRegistry. To ensure orderly metrics delivery prior to shutdown, use UpstartMetricsModule.reporterBinder to register a reporter.");
    }
  }

  private static String validateName(String name) {
    if (!LEGAL_METRIC_NAME.matcher(name).find()) {
      // This is not an IllegalArgumentException because MetricRegistry.getOrAdd would catch and discard it.
      throw new RuntimeException(
              "Metric name '" + name + "' contains illegal characters. Must match: " + LEGAL_METRIC_NAME
      );
    }
    return name;
  }

  @Override
  public <T extends Metric> T register(String name, T metric) throws IllegalArgumentException {
    return super.register(validateName(name), metric);
  }

  public <T extends Metric> T register(String name, Map<String, String> tags, T metric) throws IllegalArgumentException {
    return register(TaggedMetricName.encodedName(name, tags), metric);
  }

  public Counter counter(String name, Map<String, String> tags) {
    return super.counter(TaggedMetricName.encodedName(name, tags));
  }

  public Histogram histogram(String name, Map<String, String> tags) {
    return super.histogram(TaggedMetricName.encodedName(name, tags));
  }

  public Meter meter(String name, Map<String, String> tags) {
    return super.meter(TaggedMetricName.encodedName(name, tags));
  }

  public Timer timer(String name, Map<String, String> tags) {
    return super.timer(TaggedMetricName.encodedName(name, tags));
  }

  public boolean remove(String name, Map<String, String> tags) {
    return remove(TaggedMetricName.encodedName(name, tags));
  }

  /**
   * Registers all metrics attached to the given {@link MetricRegistry} as metrics in this one, with their names
   * prefixed with the given {@code namePrefix}.
   * <p/>
   * Unlike {@link #register}, this method will ensure that subsequent changes to the nestedRegistry will be
   * reflected in this one.
   *
   * @param namePrefix     a prefix to prepend to the names of all metrics from the {@code nestedRegistry}
   * @param nestedRegistry a {@link MetricRegistry} to expose as prefixed metrics in this one
   */
  public <R extends MetricRegistry> R registerNestedRegistry(String namePrefix, R nestedRegistry) {
    String prevName = namesByRegistry.putIfAbsent(nestedRegistry, namePrefix);
    if (prevName == null) {
      nestedRegistry.addListener(new MetricRegistryListener() {
        @Override
        public void onGaugeAdded(String name, Gauge<?> gauge) {
          registerNested(name, gauge);
        }

        @Override
        public void onGaugeRemoved(String name) {
          removeNested(name);
        }

        @Override
        public void onCounterAdded(String name, Counter counter) {
          registerNested(name, counter);
        }

        @Override
        public void onCounterRemoved(String name) {
          removeNested(name);
        }

        @Override
        public void onHistogramAdded(String name, Histogram histogram) {
          registerNested(name, histogram);
        }

        @Override
        public void onHistogramRemoved(String name) {
          removeNested(name);
        }

        @Override
        public void onMeterAdded(String name, Meter meter) {
          registerNested(name, meter);
        }

        @Override
        public void onMeterRemoved(String name) {
          removeNested(name);
        }

        @Override
        public void onTimerAdded(String name, Timer timer) {
          registerNested(name, timer);
        }

        @Override
        public void onTimerRemoved(String name) {
          removeNested(name);
        }

        private void registerNested(String name, Metric metric) {
          register(nestedName(name), metric);
        }

        private void removeNested(String name) {
          remove(nestedName(name));
        }

        private String nestedName(String name) {
          return MetricRegistry.name(namePrefix, name);
        }
      });
    } else {
      // already registered this registry, so just check that the names match
      checkArgument(prevName.equals(namePrefix), "MetricRegistry was already registered with a different namePrefix: %s / %s", namePrefix, prevName);
    }
    return nestedRegistry;
  }
}
