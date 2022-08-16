package upstart.telemetry;

import com.google.inject.Key;
import io.upstartproject.avro.MessageEnvelope;
import io.upstartproject.avro.event.ConfigValueRecord;
import io.upstartproject.avro.event.ExceptionEvent;
import io.upstartproject.avro.event.ServiceCleanShutdownEvent;
import io.upstartproject.avro.event.ServiceConfigLoadedEvent;
import io.upstartproject.avrocodec.AvroDecoder;
import io.upstartproject.avrocodec.AvroPublisher;
import io.upstartproject.avrocodec.AvroTaxonomy;
import io.upstartproject.avrocodec.EnvelopeCodec;
import io.upstartproject.avrocodec.MemorySchemaRegistry;
import io.upstartproject.avrocodec.SchemaRegistry;
import io.upstartproject.avrocodec.events.PackagedEvent;
import io.upstartproject.avrocodec.events.PackagedEventSink;
import io.upstartproject.avrocodec.upstart.AvroEnvelopeModule;
import io.upstartproject.avrocodec.upstart.AvroPublicationModule;
import io.upstartproject.avrocodec.upstart.DataStore;
import org.apache.avro.specific.SpecificRecordBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import upstart.config.UpstartModule;
import upstart.log4j.test.SuppressLogs;
import upstart.managedservices.LifecycleCoordinator;
import upstart.managedservices.ManagedServiceGraph;
import upstart.managedservices.ServiceLifecycle;
import upstart.metrics.TaggedMetricRegistry;
import upstart.test.StacklessTestException;
import upstart.test.UpstartTest;
import upstart.util.LogLevel;
import upstart.util.concurrent.CompletableFutures;
import upstart.util.concurrent.Deadline;
import upstart.util.concurrent.services.NotifyingService;
import upstart.util.concurrent.services.ServiceDependencyChecker;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static upstart.test.truth.CompletableFutureSubject.assertThat;

//@ShowServiceGraph
@SuppressLogs({LifecycleCoordinator.class, TaggedMetricRegistry.class})
@UpstartTest
public class ServiceTelemetryTest extends UpstartModule {
  private static final String ERROR_MESSAGE = "Testing service-failure";
  @Inject CapturingEventSink capturingEventSink;

  @Inject
  @ServiceLifecycle(ServiceLifecycle.Phase.Infrastructure)
  ManagedServiceGraph infrastructureServiceGraph;
  @Inject
  @ServiceLifecycle(ServiceLifecycle.Phase.Application) ManagedServiceGraph appServiceGraph;
  @Inject
  ServiceDependencyChecker dependencyChecker;
  AvroDecoder decoder;
  EnvelopeCodec envelopeCodec;
  private StacklessTestException failureException = null;

  @Override
  protected void configure() {
    install(new EventLogModule());
    install(new AvroEnvelopeModule(EventLogModule.TELEMETRY_DATA_STORE));
    bind(SchemaRegistry.class).annotatedWith(EventLogModule.TELEMETRY_DATA_STORE).to(MemorySchemaRegistry.class);
    EventLogModule.bindEventSink(binder()).to(CapturingEventSink.class);

    install(ServiceTelemetry.Module.class);
    serviceManager().manage(FailingService.class);
  }

  @BeforeEach
  void setup() {
    AvroTaxonomy taxonomy = new AvroTaxonomy(new MemorySchemaRegistry());
    decoder = new AvroDecoder(taxonomy);
    AvroPublisher avroPublisher = new AvroPublisher(taxonomy);
    envelopeCodec = new EnvelopeCodec(avroPublisher, decoder);
    taxonomy.start().join();

    avroPublisher.ensureRegistered(MessageEnvelope.class).join();
    avroPublisher.registerSpecificPackers(AvroPublisher.PackageKey.fromRecordPackage(ExceptionEvent.class)).join();
  }

  @Test
  void checkFailureEvent() throws InterruptedException {
    dependencyChecker.assertThat(FailingService.class).dependsUpon(ServiceTelemetry.class);
    dependencyChecker.assertThat(ServiceTelemetry.class).dependsUpon(Key.get(AvroPublicationModule.AvroPublicationService.class, EventLogModule.TELEMETRY_DATA_STORE));

    infrastructureServiceGraph.start().join();
    FailingService failingService = appServiceGraph.getService(FailingService.class);
    assertThat(failingService.isRunning()).isTrue();

    failureException = new StacklessTestException(ERROR_MESSAGE);
    failingService.fail(failureException);

    assertThat(infrastructureServiceGraph.getStoppedFuture()).doneWithin(Deadline.withinSeconds(5))
            .completedWithExceptionThat().isSameInstanceAs(failureException);

    List<MessageEnvelope> envelopes = capturePublishedEvents(2);

    assertThat(unpack(envelopes.get(0), ServiceConfigLoadedEvent.class)
            .getConfigEntries()).containsEntry("upstart.context.environment", new ConfigValueRecord("TEST", "UPSTART_ENVIRONMENT"));

    assertThat(unpack(envelopes.get(1), ExceptionEvent.class).getException().getMessage())
            .isEqualTo(ERROR_MESSAGE);
  }

  @Test
  void checkCleanShutdownEvent() throws ExecutionException, InterruptedException {
    while (true) {
      try {
        infrastructureServiceGraph.start().get(3, TimeUnit.SECONDS);
        break;
      } catch (TimeoutException e) {
        System.out.println("Still waiting..." + infrastructureServiceGraph);
      }
    }
    infrastructureServiceGraph.stop().join();

    List<MessageEnvelope> envelopes = capturePublishedEvents(2);
    unpack(envelopes.get(0), ServiceConfigLoadedEvent.class);
    unpack(envelopes.get(1), ServiceCleanShutdownEvent.class);
  }

  @AfterEach
  void stopStuff() {
    if (infrastructureServiceGraph != null) {
      try {
        infrastructureServiceGraph.stop().join();
      } catch (CompletionException e) {
        assertWithMessage("expected service not to fail").that(failureException).isNotNull();
        assertThat(e).hasCauseThat().isSameInstanceAs(failureException);
      }
    }
  }

  private List<MessageEnvelope> capturePublishedEvents(int expectedEventCount) {

    List<MessageEnvelope> events = capturingEventSink.events;
    assertThat(events).hasSize(expectedEventCount);
    return events;
  }

  private <T extends SpecificRecordBase> T unpack(MessageEnvelope envelope, Class<T> recordClass) {
    return envelopeCodec.makeUnpackable(envelope).join().convertMessage(decoder.recordConverter(recordClass)).orElseThrow();
  }

  @Singleton
  static class FailingService extends NotifyingService {
    volatile boolean failed = false;

    @Override
    protected void doStart() {
      notifyStarted();
    }

    void fail(Throwable throwable) {
      failed = true;
      notifyFailed(throwable);
    }

    @Override
    protected void doStop() {
      assertWithMessage("should have failed").that(failed).isFalse();
      notifyStopped();
    }
  }

  @Singleton
  static class CapturingEventSink implements PackagedEventSink {
    final List<MessageEnvelope> events = new CopyOnWriteArrayList<>();
    private final EnvelopeCodec envelopeCodec;

    @Inject
    CapturingEventSink(@DataStore(EventLogModule.TELEMETRY) EnvelopeCodec envelopeCodec) {
      this.envelopeCodec = envelopeCodec;
    }

    @Override
    public CompletableFuture<?> publish(LogLevel diagnosticLogLevel, PackagedEvent event) {
      events.add(event.toEnvelope(envelopeCodec));
      return CompletableFutures.nullFuture();
    }

    @Override
    public void flush() {

    }
  }
}
