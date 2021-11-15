package io.upstartproject.avrocodec;

import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import com.google.common.collect.Streams;
import com.google.common.io.BaseEncoding;
import com.google.common.util.concurrent.Service;
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
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * Provides utility-methods for working with {@link MessageEnvelope}s:
 */
public class EnvelopeCodec {

  private static final int UUID_BYTE_LENGTH = 2 * Long.BYTES;
  private static final BaseEncoding RANDOM_ID_ENCODING = BaseEncoding.base64Url().omitPadding();
  private final AvroCodec avroCodec;
  private final SpecificRecordUnpacker<MessageEnvelopePayload> payloadUnpacker;
  private final SpecificRecordUnpacker<MessageEnvelope> envelopeUnpacker;
  private volatile SpecificRecordPacker<MessageEnvelope> envelopeEncoder = null;

  public EnvelopeCodec(AvroCodec avroCodec) {
    this.avroCodec = avroCodec;
    payloadUnpacker = avroCodec.recordUnpacker(MessageEnvelopePayload.class);
    envelopeUnpacker = avroCodec.recordUnpacker(MessageEnvelope.class);
  }

  public CompletableFuture<EnvelopeCodec> registerEnvelopeSchema() {
    return avroCodec.ensureRegistered(Stream.of(MessageEnvelope.getClassSchema()))
            .thenApply(ignored -> this);
  }

  public AvroCodec avroCodec() {
    return avroCodec;
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
            uniqueId.orElseGet(EnvelopeCodec::newRandomId),
            eventTimestamp,
            timestampResolution,
            metadata.application(),
            metadata.owner(),
            metadata.environment(),
            metadata.deploymentStage(),
            metadata.tags()
    );
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
   * underlying {@link AvroCodec} before completing the returned {@link CompletableFuture}.
   */
  public CompletableFuture<UnpackableMessageEnvelope> loadEnvelope(InputStream in) {
    return avroCodec.readUnpackableRecord(in)
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
                    .map(avroCodec::toUnpackable)
    );

    return avroCodec.toUnpackable(envelope.getMessage()).thenCombine(
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
    TimeUnit unit;
    switch (resolution) {
      case Milliseconds:
        unit = TimeUnit.MILLISECONDS;
        break;
      case Microseconds:
        unit = TimeUnit.MICROSECONDS;
        break;
      case Nanoseconds:
        unit = TimeUnit.NANOSECONDS;
        break;
      default:
        throw new IllegalArgumentException("Unrecognized EventTimestampResolution: " + resolution);
    }

    Duration sinceEpoch = Duration.ofNanos(unit.toNanos(timestampValue));
    return Instant.EPOCH.plus(sinceEpoch);
  }

  public CompletableFuture<UnpackableRecord> extractEnvelopeMessage(InputStream in) {
    return avroCodec.toUnpackable(AvroCodec.readPackedRecord(in))
            .thenCompose(unpackable -> avroCodec.toUnpackable(payloadUnpacker.unpack(unpackable).getMessage()));
  }

  public static String newRandomId() {
    return RANDOM_ID_ENCODING.encode(toBytes(UUID.randomUUID()));
  }

  public PackableRecord<MessageEnvelope> makePackable(MessageEnvelope envelope) {
    return envelopePacker().makePackable(envelope);
  }

  public byte[] getSerializedBytes(MessageEnvelope envelope) {
    return makePackable(envelope).serialize();
  }

  private static byte[] toBytes(UUID identifier) {
    return putBytes(ByteBuffer.allocate(UUID_BYTE_LENGTH), identifier).array();
  }

  private static ByteBuffer putBytes(ByteBuffer buf, UUID identifier) {
    return buf.putLong(identifier.getMostSignificantBits())
            .putLong(identifier.getLeastSignificantBits());
  }

  private SpecificRecordPacker<MessageEnvelope> envelopePacker() {
    if (envelopeEncoder == null) {
      envelopeEncoder = avroCodec.getPreRegisteredPacker(MessageEnvelope.getClassSchema())
              .specificPacker(MessageEnvelope.class);
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
