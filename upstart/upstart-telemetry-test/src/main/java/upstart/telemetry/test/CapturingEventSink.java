package upstart.telemetry.test;

import io.upstartproject.avro.MessageEnvelope;
import io.upstartproject.avrocodec.AvroDecoder;
import io.upstartproject.avrocodec.AvroPublisher;
import io.upstartproject.avrocodec.AvroTaxonomy;
import io.upstartproject.avrocodec.EnvelopeDecoder;
import io.upstartproject.avrocodec.EnvelopePublisher;
import io.upstartproject.avrocodec.MemorySchemaRegistry;
import io.upstartproject.avrocodec.RecordTypeFamily;
import io.upstartproject.avrocodec.UnpackableMessageEnvelope;
import io.upstartproject.avrocodec.events.PackagedEvent;
import io.upstartproject.avrocodec.events.PackagedEventSink;
import io.upstartproject.avrocodec.upstart.AvroPublicationModule;
import io.upstartproject.avrocodec.upstart.DataStore;
import org.apache.avro.specific.SpecificRecordBase;
import upstart.config.UpstartModule;
import upstart.telemetry.EventLogModule;
import upstart.util.LogLevel;
import upstart.util.concurrent.CompletableFutures;
import upstart.util.concurrent.ListPromise;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Stream;

@Singleton
public class CapturingEventSink implements PackagedEventSink {
  final List<MessageEnvelope> events = new CopyOnWriteArrayList<>();
  private final EnvelopePublisher onlineCodec;
  private final AvroTaxonomy taxonomy = new AvroTaxonomy(new MemorySchemaRegistry());
  private final AvroDecoder decoder = new AvroDecoder(taxonomy);
  private final AvroPublisher offlinePublisher = new AvroPublisher(taxonomy);
  private final EnvelopeDecoder offlineCodec = new EnvelopeDecoder(decoder);

  @Inject
  CapturingEventSink(
          @DataStore(EventLogModule.TELEMETRY) EnvelopePublisher onlineCodec,
          @DataStore(EventLogModule.TELEMETRY) AvroPublicationModule.AvroPublicationService publicationService
  ) {
    taxonomy.start().join();
    this.onlineCodec = onlineCodec;
    publicationService.getStartedFuture().thenRun(() -> offlinePublisher.ensureReplicatedFrom(publicationService.getPublisher()));
  }

  @Override
  public CompletableFuture<?> publish(LogLevel diagnosticLogLevel, PackagedEvent event) {
    events.add(event.toEnvelope(onlineCodec));
    return CompletableFutures.nullFuture();
  }

  public List<MessageEnvelope> capturedEvents() {
    return events;
  }

  public List<UnpackableMessageEnvelope> unpackableEvents() {
    return events.stream().map(offlineCodec::makeUnpackable).collect(ListPromise.toListPromise()).join();
  }

  public Stream<UnpackableMessageEnvelope> findEvents(Class<? extends SpecificRecordBase> eventClass) {
    return unpackableEvents().stream().filter(event -> event.messageIsInstanceOf(taxonomy.findTypeFamily(eventClass)));
  }

  public RecordTypeFamily typeFamily(Class<? extends SpecificRecordBase> eventClass) {
    return taxonomy.findTypeFamily(eventClass);
  }

  @Override
  public CompletableFuture<?> flush() {
    return CompletableFutures.nullFuture();
  }

  public static class Module extends UpstartModule {
    @Override
    protected void configure() {
      EventLogModule.bindEventSink(binder()).to(CapturingEventSink.class);
    }
  }
}
