package upstart.telemetry;

import io.upstartproject.avro.MessageEnvelope;
import io.upstartproject.avro.event.ConfigValueRecord;
import io.upstartproject.avro.event.ExceptionEvent;
import io.upstartproject.avro.event.ServiceCleanShutdownEvent;
import io.upstartproject.avro.event.ServiceConfigLoadedEvent;
import io.upstartproject.avrocodec.AvroCodec;
import io.upstartproject.avrocodec.EnvelopeCodec;
import io.upstartproject.avrocodec.MemorySchemaRepo;
import io.upstartproject.avrocodec.SchemaRepo;
import io.upstartproject.avrocodec.UnpackableMessageEnvelope;
import io.upstartproject.avrocodec.events.PackagedEventSink;
import io.upstartproject.avrocodec.upstart.AvroModule;
import org.apache.avro.specific.SpecificRecordBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import upstart.config.UpstartModule;
import upstart.log4j.test.SuppressLogs;
import upstart.metrics.TaggedMetricRegistry;
import upstart.managedservices.LifecycleCoordinator;
import upstart.managedservices.ManagedServiceGraph;
import upstart.util.concurrent.services.NotifyingService;
import upstart.util.concurrent.services.ServiceDependencyChecker;
import upstart.managedservices.ServiceLifecycle;
import upstart.test.StacklessTestException;
import upstart.test.UpstartTest;
import upstart.util.LogLevel;
import upstart.util.concurrent.CompletableFutures;
import upstart.util.concurrent.Deadline;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static upstart.test.CompletableFutureSubject.assertThat;

//@ShowServiceGraph
@SuppressLogs({LifecycleCoordinator.class, TaggedMetricRegistry.class})
@UpstartTest
public class ServiceTelemetryTest extends UpstartModule {
  private static final String ERROR_MESSAGE = "Testing service-failure";
  private final PackagedEventSink mockEventSink = mock(PackagedEventSink.class);

  @Inject
  @ServiceLifecycle(ServiceLifecycle.Phase.Infrastructure)
  ManagedServiceGraph infrastructureServiceGraph;
  @Inject
  @ServiceLifecycle(ServiceLifecycle.Phase.Application) ManagedServiceGraph appServiceGraph;
  @Inject
  ServiceDependencyChecker dependencyChecker;
  private final AvroCodec avroCodec = new AvroCodec(new MemorySchemaRepo());
  private final EnvelopeCodec envelopeCodec = new EnvelopeCodec(avroCodec);
  private StacklessTestException failureException = null;

  @Override
  protected void configure() {
    install(new EventLogModule());
    bind(SchemaRepo.class).to(MemorySchemaRepo.class);
    EventLogModule.bindEventSink(binder()).toInstance(mockEventSink);

    install(ServiceTelemetry.Module.class);
    serviceManager().manage(FailingService.class);
  }

  @BeforeEach
  void setup() {
    avroCodec.startAsync().awaitRunning();
    avroCodec.ensureRegistered(Stream.of(MessageEnvelope.getClassSchema())).join();
    avroCodec.registerSpecificPackers(AvroCodec.PackageKey.fromRecordPackage(ExceptionEvent.class)).join();

    when(mockEventSink.publish(any(), any(), any())).thenReturn(CompletableFutures.nullFuture());
  }

  @Test
  void checkFailureEvent() throws InterruptedException {
    dependencyChecker.assertThat(FailingService.class).dependsUpon(ServiceTelemetry.class);
    dependencyChecker.assertThat(ServiceTelemetry.class).dependsUpon(AvroModule.AvroCodecService.class);

    infrastructureServiceGraph.start().join();
    FailingService failingService = appServiceGraph.getService(FailingService.class);
    assertThat(failingService.isRunning()).isTrue();

    failureException = new StacklessTestException(ERROR_MESSAGE);
    failingService.fail(failureException);

    assertThat(infrastructureServiceGraph.getStoppedFuture()).doneWithin(Deadline.withinSeconds(5))
            .completedWithExceptionThat().isSameInstanceAs(failureException);

    List<UnpackableMessageEnvelope> envelopes = capturePublishedEvents(2);

    UnpackableMessageEnvelope configEnvelope = envelopes.get(0);
    assertThat(unpack(configEnvelope, ServiceConfigLoadedEvent.class)
            .getConfigEntries()).containsEntry("upstart.context.environment", new ConfigValueRecord("TEST", "UPSTART_ENVIRONMENT"));

    UnpackableMessageEnvelope exceptionEnvelope = envelopes.get(1);
    assertThat(unpack(exceptionEnvelope, ExceptionEvent.class).getException().getMessage())
            .isEqualTo(ERROR_MESSAGE);
  }

  @Test
  void checkCleanShutdownEvent() throws ExecutionException, InterruptedException {
//    infrastructureServiceGraph.start().join();
    boolean waiting;
    while (true) {
      try {
        infrastructureServiceGraph.start().get(3, TimeUnit.SECONDS);
        break;
      } catch (TimeoutException e) {
        System.out.println("Still waiting..." + infrastructureServiceGraph);
      }
    }
    infrastructureServiceGraph.stop().join();

    List<UnpackableMessageEnvelope> envelopes = capturePublishedEvents(2);
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

  private List<UnpackableMessageEnvelope> capturePublishedEvents(int expectedEventCount) {
    ArgumentCaptor<byte[]> bytesCaptor = ArgumentCaptor.forClass(byte[].class);
    Mockito.verify(mockEventSink, Mockito.times(expectedEventCount)).publish(any(), any(), bytesCaptor.capture());

    List<byte[]> eventBytes = bytesCaptor.getAllValues();
    assertThat(eventBytes).hasSize(expectedEventCount);
    return CompletableFutures.allAsList(eventBytes.stream().map(envelopeCodec::loadEnvelope)).join();
  }

  private <T extends SpecificRecordBase> T unpack(UnpackableMessageEnvelope envelope, Class<T> recordClass) {
    return envelope.convertMessage(avroCodec.recordConverter(recordClass)).get();
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
}
