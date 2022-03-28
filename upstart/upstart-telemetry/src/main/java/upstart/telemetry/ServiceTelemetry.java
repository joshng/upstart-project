package upstart.telemetry;

import com.codahale.metrics.Gauge;
import com.codahale.metrics.MetricRegistry;
import com.google.common.util.concurrent.Service;
import io.upstartproject.avro.event.ConfigValueRecord;
import io.upstartproject.avro.event.ServiceCleanShutdownEvent;
import io.upstartproject.avro.event.ServiceConfigLoadedEvent;
import io.upstartproject.avro.event.ExceptionEvent;
import io.upstartproject.avrocodec.upstart.AvroModule;
import io.upstartproject.avrocodec.upstart.EventLogger;
import upstart.config.ConfigDump;
import upstart.config.UpstartApplicationConfig;
import upstart.config.UpstartModule;
import upstart.metrics.annotations.Metered;
import upstart.services.IdleService;
import upstart.services.ManagedServicesModule;
import upstart.services.ServiceLifecycle;
import upstart.util.LogLevel;
import upstart.util.collect.PairStream;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
@ServiceLifecycle(ServiceLifecycle.Phase.Infrastructure)
public class ServiceTelemetry extends IdleService {
  private final ExceptionRecordBuilder exceptionRecordBuilder;
  private final EventLogger<ExceptionEvent> errorEventLogger;
  private final EventLogger<ServiceConfigLoadedEvent> configLogger;
  private final EventLogger<ServiceCleanShutdownEvent> shutdownEventLogger;
  private final UpstartApplicationConfig appConfig;
  private boolean failureDetected = false;

  @Inject
  public ServiceTelemetry(
          ExceptionRecordBuilder exceptionRecordBuilder,
          EventLogger<ExceptionEvent> errorEventLogger,
          EventLogger<ServiceConfigLoadedEvent> configLogger,
          EventLogger<ServiceCleanShutdownEvent> shutdownEventLogger,
          UpstartApplicationConfig appConfig,
          MetricRegistry metricRegistry
  ) {
    this.exceptionRecordBuilder = exceptionRecordBuilder;
    this.errorEventLogger = errorEventLogger;
    this.configLogger = configLogger;
    this.appConfig = appConfig;
    this.shutdownEventLogger = shutdownEventLogger;

    metricRegistry.register(MetricRegistry.name(ServiceTelemetry.class, "liveness"),
            (Gauge<Integer>) () -> isRunning() ? 1 : 0
    );
  }

  @Override
  protected void startUp() {
    ServiceConfigLoadedEvent configEvent = new ServiceConfigLoadedEvent(PairStream.withMappedKeys(
            ConfigDump.describeValues(appConfig.activeConfig(), Integer.MAX_VALUE), ConfigDump.ValueDump::key)
            .mapValues(v -> new ConfigValueRecord(v.value(), v.origin()))
            .toImmutableMap()
    );
    // TODO: should we proceed with startup if this event-delivery fails?
    // for now: no, running without telemetry is running blind. better to prevent a deployment than risk ignoring bad configuration
    configLogger.publish(LogLevel.Info, configEvent).join();
  }

  @Override
  protected synchronized void shutDown() {
    if (!failureDetected) {
      shutdownEventLogger.publish(LogLevel.Info, new ServiceCleanShutdownEvent());
    }
  }

  @Metered
  synchronized void failed(Throwable failure) {
    failureDetected = true;
    ExceptionEvent exceptionEvent = exceptionRecordBuilder.toExceptionEvent(failure);
    errorEventLogger.publish(LogLevel.Info, exceptionEvent);
  }

  public static class Module extends UpstartModule {
    @Override
    protected void configure() {
      install(EventLogModule.class);
      serviceManager().manage(ServiceTelemetry.class);
      AvroModule.bindAvroFromRecordPackage(binder(), ServiceConfigLoadedEvent.class);
      ManagedServicesModule.bindServiceListener(binder()).to(ServiceListener.class);
    }
  }

  static class ServiceListener extends Service.Listener {
    private final ServiceTelemetry telemetry;

    @Inject
    ServiceListener(ServiceTelemetry telemetry) {
      this.telemetry = telemetry;
    }

    @Override
    public void failed(State from, Throwable failure) {
      telemetry.failed(failure);
    }
  }
}
