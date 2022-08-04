package upstart.metrics;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Counting;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.Meter;
import com.codahale.metrics.Metered;
import com.codahale.metrics.MetricRegistryListener;
import com.codahale.metrics.Reporter;
import com.codahale.metrics.Sampling;
import com.codahale.metrics.Snapshot;
import com.codahale.metrics.Timer;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import com.google.common.reflect.TypeToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Type;
import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.DoubleSupplier;
import java.util.stream.Stream;

/**
 * Exposes a codahale MetricRegistry as a source of DoubleSuppliers, dynamically passing each registered metric
 * to a provided {@link MetricReporter}
 */
public class CodahaleMetricsAdapter implements MetricRegistryListener {
  private static final Logger LOG = LoggerFactory.getLogger(CodahaleMetricsAdapter.class);
  private static final Type GAUGE_VALUE_TYPE = Gauge.class.getTypeParameters()[0];
  private static final Optional<String> COUNT_CLASSIFIER = Optional.of("Count");

  private final MetricReporter metricConsumer;
  private final Clock clock;
  private final long snapshotFrequencyMillis;
  private final Set<HistogramMetric> histogramMetrics;
  private final Set<MeterMetric> meterMetrics;
  private final OptionalDouble durationScale;

  public CodahaleMetricsAdapter(
          Iterable<HistogramMetric> histogramMetrics,
          Iterable<MeterMetric> meterMetrics,
          TimeUnit timerPublishUnit,
          Duration snapshotFrequency,
          MetricReporter reporter,
          Clock clock
  ) {
    this.metricConsumer = reporter;
    this.clock = clock;
    this.histogramMetrics = Sets.newEnumSet(histogramMetrics, HistogramMetric.class);
    this.meterMetrics = Sets.newEnumSet(meterMetrics, MeterMetric.class);
    this.snapshotFrequencyMillis = snapshotFrequency.toMillis();
    durationScale = OptionalDouble.of(1.0 / timerPublishUnit.toNanos(1));
  }

  @SuppressWarnings("unchecked")
  @Override
  public void onGaugeAdded(String encodedName, Gauge<?> gauge) {
    TypeToken<?> returnType = TypeToken.of(gauge.getClass()).resolveType(GAUGE_VALUE_TYPE);
    if (Number.class.isAssignableFrom(returnType.getRawType()) || gauge.getValue() instanceof Number) {
      Gauge<? extends Number> numberGauge = (Gauge<? extends Number>) gauge;
      register(encodedName, Stream.of(new MetricSource(encodedName, Optional.empty(), () -> numberGauge.getValue()
              .doubleValue())));
    } else {
      LOG.info("Omitted non-numeric gauge: {}", encodedName);
    }
  }

  @Override
  public void onGaugeRemoved(String encodedName) {
    remove(encodedName);
  }

  @Override
  public void onCounterAdded(String encodedName, Counter counter) {
    register(encodedName, adaptCountingMetric(encodedName, Optional.empty(), counter));
  }

  @Override
  public void onCounterRemoved(String encodedName) {
    remove(encodedName);
  }

  @Override
  public void onHistogramAdded(String encodedName, Histogram histogram) {
    register(encodedName, Stream.concat(
            adaptCountingMetric(encodedName, COUNT_CLASSIFIER, histogram),
            adaptSamplingMetric(encodedName, histogram, OptionalDouble.empty()))
    );
  }

  @Override
  public void onHistogramRemoved(String encodedName) {
    remove(encodedName);
  }

  @Override
  public void onMeterAdded(String encodedName, Meter meter) {
    register(encodedName, Stream.concat(
            adaptCountingMetric(encodedName, COUNT_CLASSIFIER, meter),
            adaptMeterMetric(encodedName, meter, OptionalDouble.empty())));
  }

  @Override
  public void onMeterRemoved(String encodedName) {
    remove(encodedName);
  }

  @Override
  public void onTimerAdded(String encodedName, Timer timer) {

    Stream<MetricSource> resultingStream = Stream.concat(
            Stream.concat(adaptCountingMetric(encodedName, COUNT_CLASSIFIER, timer),
                    adaptSamplingMetric(encodedName, timer, durationScale)),
            adaptMeterMetric(encodedName, timer, OptionalDouble.empty()));

    register(encodedName, resultingStream);
  }

  @Override
  public void onTimerRemoved(String encodedName) {
    remove(encodedName);
  }

  private void register(String encodedName, Stream<MetricSource> metricSources) {
    metricConsumer.registerMetric(encodedName, ImmutableList.copyOf(metricSources.iterator()));
  }

  private void remove(String encodedName) {
    metricConsumer.removeMetric(encodedName);
  }

  private Stream<MetricSource> adaptCountingMetric(String name, Optional<String> classifier, Counting counter) {
    return Stream.of(new MetricSource(name, classifier, counter::getCount));
  }


  private Stream<MetricSource> adaptSamplingMetric(String name, Sampling metric, OptionalDouble scaleMaybe) {
    return new SamplingAdapter(metric).adaptMetrics(name, scaleMaybe);
  }

  private Stream<MetricSource> adaptMeterMetric(String name, Metered metric, OptionalDouble scaleMaybe) {
    return new MeteredAdapter(metric).adaptMetrics(name, scaleMaybe);
  }

  public interface MetricReporter extends Reporter {
    void registerMetric(String identifier, ImmutableList<MetricSource> metricSources);

    void removeMetric(String identifier);
  }

  public static class MetricSource {
    private final String name;
    private final Optional<String> valueClassifier;
    private final DoubleSupplier valueSupplier;

    public MetricSource(String name, Optional<String> valueClassifier, DoubleSupplier valueSupplier) {
      this.name = name;
      this.valueClassifier = valueClassifier;
      this.valueSupplier = valueSupplier;
    }

    public String getName() {
      return name;
    }

    public Optional<String> getValueClassifier() {
      return valueClassifier;
    }

    public double getCurrentValue() {
      return valueSupplier.getAsDouble();
    }
  }

  static class SnapshotContainer {
    final long expiryTimestamp;
    final Snapshot snapshot;

    private SnapshotContainer() {
      this(Long.MIN_VALUE, null);
    }

    SnapshotContainer(long millis, Snapshot snapshot) {
      expiryTimestamp = millis;
      this.snapshot = snapshot;
    }

    boolean isExpired(long now) {
      return expiryTimestamp <= now;
    }
  }

  class SamplingAdapter {
    private final Sampling metric;
    private volatile SnapshotContainer snapshotContainer = new SnapshotContainer();

    SamplingAdapter(Sampling metric) {
      this.metric = metric;
    }

    Stream<MetricSource> adaptMetrics(String name, OptionalDouble scaleMaybe) {
      return histogramMetrics.stream().map(histogramMetric -> {
        Optional<String> histogramName = Optional.of(histogramMetric.toString());
        if (scaleMaybe.isPresent()) {
          double scale = scaleMaybe.getAsDouble();
          return new MetricSource(name, histogramName, () -> getValue(histogramMetric) * scale);
        } else {
          return new MetricSource(name, histogramName, () -> getValue(histogramMetric));
        }
      });
    }

    protected double getValue(HistogramMetric histogramMetric) {
      return histogramMetric.getValue(getSnapshot(clock.millis()));
    }

    Snapshot getSnapshot(long now) {
      if (snapshotContainer.isExpired(now)) {
        // Note that this performs no synchronization, so contention can lead to superfluous snapshots.
        // ... so, this isn't really intended for high contention, but we don't expect any.
        snapshotContainer = new SnapshotContainer(now + snapshotFrequencyMillis, metric.getSnapshot());
      }
      return snapshotContainer.snapshot;
    }
  }

  class MeteredAdapter {
    private final Metered metric;

    MeteredAdapter(Metered metric) {
      this.metric = metric;
    }

    Stream<MetricSource> adaptMetrics(String name, OptionalDouble scaleMaybe) {
      return meterMetrics.stream().map(meterMetric -> {
        Optional<String> meterName = Optional.of(meterMetric.toString());

        if (scaleMaybe.isPresent()) {
          double scale = scaleMaybe.getAsDouble();
          return new MetricSource(name, meterName, () -> getValue(meterMetric) * scale);
        } else {
          return new MetricSource(name, meterName, () -> getValue(meterMetric));
        }
      });
    }

    protected double getValue(MeterMetric meterMetric) {
      return meterMetric.getValue(metric);
    }
  }
}
