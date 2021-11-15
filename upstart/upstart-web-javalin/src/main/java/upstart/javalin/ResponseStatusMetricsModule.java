package upstart.javalin;

import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import upstart.config.UpstartModule;
import io.javalin.core.JavalinConfig;
import org.eclipse.jetty.http.HttpStatus;

import javax.inject.Inject;

public class ResponseStatusMetricsModule extends UpstartModule implements JavalinWebModule {
  @Override
  protected void configure() {
    addJavalinWebBinding().to(MetricsInitializer.class);
  }

  static class MetricsInitializer implements JavalinWebInitializer {
    public static final int MAX_STATUS_CODE = HttpStatus.MAX_CODE;
    private final MetricRegistry metricRegistry;
    private final Meter[] statusCodeMeters = new Meter[MAX_STATUS_CODE + 1];

    @Inject
    MetricsInitializer(MetricRegistry metricRegistry) {
      this.metricRegistry = metricRegistry;
    }

    @Override
    public void initializeWeb(JavalinConfig config) {
      config.registerPlugin(javalin -> {
        javalin.after(ctx -> statusMeter(ctx.status()).mark());
      });
    }

    private Meter statusMeter(int statusCode) {
      Meter meter;
      boolean useCache = statusCode <= MAX_STATUS_CODE;
      if (useCache) {
        meter = statusCodeMeters[statusCode];
      } else {
        meter = null;
      }
      if (meter == null) {
        meter = metricRegistry.meter(MetricRegistry.name(JavalinWebServer.class, "responseStatus", Integer.toString(statusCode)));
        if (useCache) statusCodeMeters[statusCode] = meter;
      }
      return meter;
    }
  }
}
