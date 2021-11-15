package upstart.metrics;

import com.codahale.metrics.Snapshot;

public enum HistogramMetric {
  Mean {
    @Override
    public double getValue(Snapshot snapshot) {
      return snapshot.getMean();
    }
  },
  Min {
    @Override
    public double getValue(Snapshot snapshot) {
      return snapshot.getMin();
    }
  },
  Max {
    @Override
    public double getValue(Snapshot snapshot) {
      return snapshot.getMax();
    }
  },
  P50 {
    @Override
    public double getValue(Snapshot snapshot) {
      return snapshot.getMedian();
    }
  },
  P75 {
    @Override
    public double getValue(Snapshot snapshot) {
      return snapshot.get75thPercentile();
    }
  },
  P95 {
    @Override
    public double getValue(Snapshot snapshot) {
      return snapshot.get95thPercentile();
    }
  },
  P99 {
    @Override
    public double getValue(Snapshot snapshot) {
      return snapshot.get99thPercentile();
    }
  },
  P999 {
    @Override
    public double getValue(Snapshot snapshot) {
      return snapshot.get999thPercentile();
    }
  };

  public abstract double getValue(Snapshot snapshot);
}
