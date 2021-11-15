package upstart.metrics;

import com.codahale.metrics.Metered;

public enum MeterMetric {
  M1_RATE {
    @Override
    public double getValue(Metered meter) {
      return meter.getOneMinuteRate();
    }
  },
  M5_RATE {
    @Override
    public double getValue(Metered meter) {
      return meter.getFiveMinuteRate();
    }
  },
  M15_RATE {
    @Override
    public double getValue(Metered meter) {
      return meter.getFifteenMinuteRate();
    }
  },
  MEAN_RATE {
    @Override
    public double getValue(Metered meter) {
      return meter.getMeanRate();
    }
  };

  public abstract double getValue(Metered meter);
}
