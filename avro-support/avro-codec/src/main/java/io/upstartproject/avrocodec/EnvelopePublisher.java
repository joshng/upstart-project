package io.upstartproject.avrocodec;

import com.google.common.collect.Lists;
import io.upstartproject.avro.EventTimestampResolution;
import io.upstartproject.avro.MessageEnvelope;
import io.upstartproject.avro.PackedRecord;
import upstart.util.strings.RandomId;

import javax.inject.Inject;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

public class EnvelopePublisher {
  public static final SpecificRecordType<MessageEnvelope> MESSAGE_ENVELOPE_TYPE = SpecificRecordType.of(MessageEnvelope.class);
  protected final AvroPublisher avroPublisher;
  private volatile SpecificRecordPacker<MessageEnvelope> envelopeEncoder = null;

  @Inject
  public EnvelopePublisher(AvroPublisher avroPublisher) {
    this.avroPublisher = avroPublisher;
  }

  public CompletableFuture<EnvelopePublisher> registerEnvelopeSchema() {
    return avroPublisher.ensureRegistered(Stream.of(MESSAGE_ENVELOPE_TYPE)).thenApply(ignored -> this);
  }

  public AvroPublisher avroPublisher() {
    return avroPublisher;
  }

  public MessageEnvelope buildMessageEnvelope(
          Instant timestamp,
          Optional<String> uniqueId,
          PackedRecord message,
          MessageMetadata metadata,
          PackedRecord... annotations
  ) {
    return buildMessageEnvelope(timestamp, uniqueId, message, metadata, Lists.newArrayList(annotations));
  }

  public MessageEnvelope buildMessageEnvelope(
          Instant timestamp,
          Optional<String> uniqueId,
          PackedRecord record,
          MessageMetadata metadata,
          List<PackedRecord> annotations
  ) {
    return buildMessageEnvelope(
            timestamp.toEpochMilli(),
            EventTimestampResolution.Milliseconds,
            uniqueId,
            record,
            metadata,
            annotations
    );
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

  public PackableRecord<MessageEnvelope> makePackable(MessageEnvelope envelope) {
    return envelopePacker().makePackable(envelope);
  }

  private SpecificRecordPacker<MessageEnvelope> envelopePacker() {
    if (envelopeEncoder == null) {
      envelopeEncoder = avroPublisher.getPreRegisteredPacker(MessageEnvelope.class);
    }
    return envelopeEncoder;
  }

  public MessageEnvelope buildMessageEnvelope(
          Instant timestamp,
          Optional<String> uniqueId,
          PackableRecord<?> record,
          MessageMetadata metadata,
          List<PackableRecord<?>> annotations
  ) {
    return buildMessageEnvelope(
            timestamp,
            uniqueId,
            record.packedRecord(),
            metadata,
            Lists.transform(annotations, PackableRecord::packedRecord)
    );
  }

  public PackableRecord<MessageEnvelope> packableMessageEnvelope(
          Instant timestamp,
          Optional<String> uniqueId,
          PackableRecord<?> record,
          MessageMetadata metadata,
          PackableRecord<?>... annotations
  ) {
    return packableMessageEnvelope(timestamp, uniqueId, record, metadata, Arrays.asList(annotations));
  }

  public PackableRecord<MessageEnvelope> packableMessageEnvelope(
          Instant timestamp,
          Optional<String> uniqueId,
          PackableRecord<?> record,
          MessageMetadata metadata,
          List<? extends PackableRecord<?>> annotations
  ) {
    return makePackable(buildMessageEnvelope(
            timestamp,
            uniqueId,
            record.packedRecord(),
            metadata,
            Lists.transform(annotations, PackableRecord::packedRecord)
    ));
  }

  public PackableRecord<MessageEnvelope> packableMessageEnvelope(
          long timestamp,
          EventTimestampResolution timestampResolution,
          Optional<String> uniqueId,
          PackableRecord<?> record,
          MessageMetadata metadata,
          List<PackableRecord<?>> annotations
  ) {
    return makePackable(buildMessageEnvelope(
            timestamp,
            timestampResolution,
            uniqueId,
            record.packedRecord(),
            metadata,
            Lists.transform(annotations, PackableRecord::packedRecord)
    ));
  }


  public byte[] getSerializedBytes(MessageEnvelope envelope) {
    return makePackable(envelope).serialize();
  }
}
