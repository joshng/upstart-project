package io.upstartproject.avrocodec;

import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import com.google.common.collect.Streams;
import io.upstartproject.avro.EventTimestampResolution;
import io.upstartproject.avro.MessageEnvelope;
import io.upstartproject.avro.MessageEnvelopePayload;
import io.upstartproject.avro.PackedRecord;
import upstart.util.concurrent.CompletableFutures;
import upstart.util.exceptions.UncheckedIO;
import org.apache.avro.file.DataFileReader;
import org.apache.avro.file.DataFileStream;
import org.apache.avro.file.SeekableInput;
import org.apache.avro.specific.SpecificDatumReader;
import upstart.util.strings.RandomId;

import javax.inject.Inject;
import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * Provides utility-methods for working with {@link MessageEnvelope}s:
 */
public class EnvelopeCodec {

  public static final SpecificRecordType<MessageEnvelope> MESSAGE_ENVELOPE_TYPE = SpecificRecordType.of(MessageEnvelope.class);
  private final AvroPublisher avroPublisher;
  private final SpecificRecordUnpacker<MessageEnvelopePayload> payloadUnpacker;
  private final SpecificRecordUnpacker<MessageEnvelope> envelopeUnpacker;
  private final AvroDecoder decoder;
  private volatile SpecificRecordPacker<MessageEnvelope> envelopeEncoder = null;

  @Inject
  public EnvelopeCodec(AvroPublisher avroPublisher, AvroDecoder decoder) {
    this.avroPublisher = avroPublisher;
    payloadUnpacker = decoder.recordUnpacker(MessageEnvelopePayload.class);
    envelopeUnpacker = decoder.recordUnpacker(MessageEnvelope.class);
    this.decoder = decoder;
  }

  public CompletableFuture<EnvelopeCodec> registerEnvelopeSchema() {
    return avroPublisher.ensureRegistered(Stream.of(MESSAGE_ENVELOPE_TYPE)).thenApply(ignored -> this);
  }

  public AvroPublisher publisher() {
    return avroPublisher;
  }

  public MessageEnvelope buildMessageEnvelope(Instant timestamp, Optional<String> uniqueId, PackedRecord message, MessageMetadata metadata, PackedRecord... annotations) {
    return buildMessageEnvelope(timestamp, uniqueId, message, metadata, Lists.newArrayList(annotations));
  }

  public MessageEnvelope buildMessageEnvelope(
          Instant timestamp,
          Optional<String> uniqueId,
          PackedRecord record,
          MessageMetadata metadata,
          List<PackedRecord> annotations
  ) {
    return buildMessageEnvelope(timestamp.toEpochMilli(), EventTimestampResolution.Milliseconds, uniqueId, record, metadata, annotations);
  }

  public MessageEnvelope buildMessageEnvelope(
          long eventTimestamp,
          EventTimestampResolution timestampResolution,
          Optional<String> uniqueId,
          PackedRecord record,
          MessageMetadata metadata,
          List<PackedRecord> annotations
  ) {
    return new MessageEnvelope(
            record,
            annotations,
            uniqueId.orElseGet(RandomId::newRandomId),
            eventTimestamp,
            timestampResolution,
            metadata.application(),
            metadata.owner(),
            metadata.environment(),
            metadata.deploymentStage(),
            metadata.tags()
    );
  }

  public CompletableFuture<UnpackableMessageEnvelope> loadEnvelope(ByteBuffer bytes) {
    return loadEnvelope(AvroDecoder.byteBufferInputStream(bytes));
  }

  /**
   * Deserializes the given bytes as a {@link MessageEnvelope}, and then prepares an
   * {@link UnpackableMessageEnvelope} which can be used to deserialize its contents.
   */
  public CompletableFuture<UnpackableMessageEnvelope> loadEnvelope(byte[] bytes) {
    return loadEnvelope(new ByteArrayInputStream(bytes));
  }

  /**
   * Deserializes a {@link MessageEnvelope} from the provided input, and then prepares an
   * {@link UnpackableMessageEnvelope} which can be used to deserialize its contents.
   * <p/>
   * Ensures that * all {@link SchemaFingerprint fingerprints} referenced by the envelope are resolved by the
   * underlying {@link AvroPublisher} before completing the returned {@link CompletableFuture}.
   */
  public CompletableFuture<UnpackableMessageEnvelope> loadEnvelope(InputStream in) {
    return decoder.readUnpackableRecord(in)
            .thenCompose(unpackableEnvelope -> makeUnpackable(unpackableEnvelope.unpackWith(envelopeUnpacker)));
  }

  public Stream<CompletableFuture<UnpackableMessageEnvelope>> readEnvelopeFile(InputStream in) throws IOException {
    return asUnpackableStream(new DataFileStream<>(in, new SpecificDatumReader<>(MessageEnvelope.class)));
  }

  public Stream<CompletableFuture<UnpackableMessageEnvelope>> readEnvelopeFile(SeekableInput in) throws IOException {
    return asUnpackableStream(new DataFileReader<>(in, new SpecificDatumReader<>(MessageEnvelope.class)));
  }

  private <I extends Iterator<MessageEnvelope> & Closeable> Stream<CompletableFuture<UnpackableMessageEnvelope>> asUnpackableStream(I iterator) {
    return Streams.stream(Iterators.transform(iterator, this::makeUnpackable))
            .onClose(UncheckedIO.runnable(iterator::close));
  }

  public CompletableFuture<UnpackableMessageEnvelope> makeUnpackable(MessageEnvelope envelope) {
    MessageMetadata metadata = MessageMetadata.builder()
            .application(envelope.getApplication())
            .owner(envelope.getOwner())
            .environment(envelope.getEnvironment())
            .tags(envelope.getTags())
            .deploymentStage(envelope.getDeploymentStage())
            .build();

    CompletableFuture<List<UnpackableRecord>> annotationRecords = CompletableFutures.allAsList(
            envelope.getAnnotations().stream()
                    .map(decoder::toUnpackable)
    );

    return decoder.toUnpackable(envelope.getMessage()).thenCombine(
            annotationRecords,
            (messageDecoder, annotationList) -> {


              Instant timestamp = toInstant(envelope.getEventTimestamp(), envelope.getTimestampResolution());
              return UnpackableMessageEnvelope.builder()
                      .messageRecord(messageDecoder)
                      .uniqueId(envelope.getUniqueId())
                      .timestamp(timestamp)
                      .metadata(metadata)
                      .annotationRecords(annotationList)
                      .rawEnvelope(envelope)
                      .build();
            }
    );
  }

  public static Instant toInstant(long timestampValue, EventTimestampResolution resolution) {
    TimeUnit unit = switch (resolution) {
      case Milliseconds -> TimeUnit.MILLISECONDS;
      case Microseconds -> TimeUnit.MICROSECONDS;
      case Nanoseconds -> TimeUnit.NANOSECONDS;
      case Seconds -> TimeUnit.SECONDS;
//      default -> throw new IllegalArgumentException("Unrecognized EventTimestampResolution: " + resolution);
    };

    Duration sinceEpoch = Duration.ofNanos(unit.toNanos(timestampValue));
    return Instant.EPOCH.plus(sinceEpoch);
  }

  public CompletableFuture<UnpackableRecord> extractEnvelopeMessage(InputStream in) {
    return decoder.toUnpackable(AvroDecoder.readPackedRecord(in))
            .thenCompose(unpackable -> decoder.toUnpackable(payloadUnpacker.unpack(unpackable).getMessage()));
  }

  public PackableRecord<MessageEnvelope> makePackable(MessageEnvelope envelope) {
    return envelopePacker().makePackable(envelope);
  }

  public byte[] getSerializedBytes(MessageEnvelope envelope) {
    return makePackable(envelope).serialize();
  }

  private SpecificRecordPacker<MessageEnvelope> envelopePacker() {
    if (envelopeEncoder == null) {
      envelopeEncoder = avroPublisher.getPreRegisteredPacker(MessageEnvelope.class);
    }
    return envelopeEncoder;
  }

  public MessageEnvelope buildMessageEnvelope(Instant timestamp, Optional<String> uniqueId, PackableRecord<?> record, MessageMetadata metadata, List<PackableRecord<?>> annotations) {
    return buildMessageEnvelope(timestamp, uniqueId, record.packedRecord(), metadata, Lists.transform(annotations, PackableRecord::packedRecord));
  }

  public PackableRecord<MessageEnvelope> packableMessageEnvelope(Instant timestamp, Optional<String> uniqueId, PackableRecord<?> record, MessageMetadata metadata, PackableRecord<?>... annotations) {
    return packableMessageEnvelope(timestamp, uniqueId, record, metadata, Arrays.asList(annotations));
  }

  public PackableRecord<MessageEnvelope> packableMessageEnvelope(Instant timestamp, Optional<String> uniqueId, PackableRecord<?> record, MessageMetadata metadata, List<PackableRecord<?>> annotations) {
    return makePackable(buildMessageEnvelope(timestamp, uniqueId, record.packedRecord(), metadata, Lists.transform(annotations, PackableRecord::packedRecord)));
  }

  public PackableRecord<MessageEnvelope> packableMessageEnvelope(long timestamp, EventTimestampResolution timestampResolution, Optional<String> uniqueId, PackableRecord<?> record, MessageMetadata metadata, List<PackableRecord<?>> annotations) {
    return makePackable(buildMessageEnvelope(timestamp, timestampResolution, uniqueId, record.packedRecord(), metadata, Lists.transform(annotations, PackableRecord::packedRecord)));
  }
}
