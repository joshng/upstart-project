package upstart.telemetry;

import com.codahale.metrics.Gauge;
import com.codahale.metrics.MetricRegistry;
import com.google.common.util.concurrent.Service;
import io.upstartproject.avro.event.ConfigValueRecord;
import io.upstartproject.avro.event.ServiceCleanShutdownEvent;
import io.upstartproject.avro.event.ServiceConfigLoadedEvent;
import io.upstartproject.avro.event.ExceptionEvent;
import io.upstartproject.avrocodec.upstart.AvroPublicationModule;
import io.upstartproject.avrocodec.upstart.EventLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import upstart.config.ConfigDump;
import upstart.config.UpstartApplicationConfig;
import upstart.config.UpstartModule;
import upstart.metrics.annotations.Metered;
import upstart.util.concurrent.services.IdleService;
import upstart.managedservices.ManagedServicesModule;
import upstart.managedservices.ServiceLifecycle;
import upstart.util.LogLevel;
import upstart.util.collect.PairStream;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
@ServiceLifecycle(ServiceLifecycle.Phase.Infrastructure)
public class ServiceTelemetry extends IdleService {
  private static final Logger LOG = LoggerFactory.getLogger(ServiceTelemetry.class);
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
    configLogger.publish(LogLevel.Debug, configEvent).join();
  }

  @Override
  protected synchronized void shutDown() {
    if (!failureDetected) {
      shutdownEventLogger.publish(LogLevel.Info, new ServiceCleanShutdownEvent());
    }
  }

  @Metered
  synchronized protected void failed(Throwable failure) {
    failureDetected = true;
    if (isRunning()) {
      ExceptionEvent exceptionEvent = exceptionRecordBuilder.toExceptionEvent(failure);
      errorEventLogger.publish(LogLevel.Info, exceptionEvent);
    } else {
      LOG.error("Service failed outside of normal running-phase", failure);
    }
  }

  public static class Module extends UpstartModule {
    @Override
    protected void configure() {
      install(new EventLogModule());
      serviceManager().manage(ServiceTelemetry.class);
      AvroPublicationModule.publishAvroFromRecordPackage(binder(), EventLogModule.TELEMETRY_DATA_STORE, ServiceConfigLoadedEvent.class);
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
