package io.upstartproject.avrocodec;

import io.upstartproject.avro.MessageEnvelope;
import upstart.util.collect.MoreStreams;
import org.apache.avro.specific.SpecificRecordBase;
import org.immutables.value.Value;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * A utility wrapper for unpacking a {@link MessageEnvelope}, providing {@link UnpackableRecord} support for deserializing
 * the {@link #messageRecord} and all of its {@link #annotationRecords}.
 * <p/>
 * Also assists with {@link #findAnnotationRecords finding annotations} belonging to a specific
 * {@link AvroCodec.RecordTypeFamily} (which is a necessary subtlety for accommodating schema-evolution of annotations).
 */
@Value.Immutable
public interface UnpackableMessageEnvelope {
  static ImmutableUnpackableMessageEnvelope.Builder builder() {
    return ImmutableUnpackableMessageEnvelope.builder();
  }

  @Value.Auxiliary
  String uniqueId();

  @Value.Auxiliary
  Instant timestamp();

  @Value.Auxiliary
  UnpackableRecord messageRecord();

  @Value.Auxiliary
  MessageMetadata metadata();

  @Value.Auxiliary
  List<UnpackableRecord> annotationRecords();

  MessageEnvelope rawEnvelope();

  /**
   * Determines if the {@link #messageRecord} embedded in this envelope is a member of the given {@link AvroCodec.RecordTypeFamily}
   * (which can be obtained with {@link AvroCodec#findTypeFamily}).
   * <p/>
   * Identical to {@code typeFamily.isInstance(messageRecord())} (which may be more convenient to call directly).
   */
  default boolean messageIsInstanceOf(AvroCodec.RecordTypeFamily typeFamily) {
    return typeFamily.isInstance(messageRecord());
  }

  /**
   * Converts this envelope's {@link #messageRecord} with the given converter if it is a member of the converter's
   * {@link RecordConverterApi#writerTypeFamily}.
   * @return an {@link Optional} containing the converted message, if it matches the converter's target {@link AvroCodec.RecordTypeFamily};
   * otherwise, {@link Optional#empty}
   *
   * @see #messageIsInstanceOf
   * @see AvroCodec#findTypeFamily(String)
   */
  default <T extends SpecificRecordBase> Optional<T> convertMessage(RecordConverterApi<T> converter) {
    return messageIsInstanceOf(converter.writerTypeFamily())
            ? Optional.of(converter.convert(messageRecord()))
            : Optional.empty();
  }

  /**
   * Finds annotations on this envelope matching the given typeFamily.
   */
  default Stream<UnpackableRecord> findAnnotationRecords(AvroCodec.RecordTypeFamily typeFamily) {
    return annotationRecords().stream()
            .filter(typeFamily::isInstance);
  }

  /**
   * Finds and converts annotations on this envelope matching the given converter's
   * {@link RecordConverterApi#writerTypeFamily()}.
   */
  default <T extends SpecificRecordBase> Stream<T> convertAnnotations(RecordConverterApi<T> converter) {
    return findAnnotationRecords(converter.writerTypeFamily()).map(converter::convert);
  }

  /**
   * Finds the first annotation on this envelope matching the given typeFamily.
   * @throws NoSuchElementException if no matching annotation is found
   */
  default UnpackableRecord getRequiredAnnotationRecord(AvroCodec.RecordTypeFamily typeFamily) {
    return findAnnotationRecords(typeFamily).findFirst()
            .orElseThrow(() -> new NoSuchElementException(String.format("Annotation not found: %s (envelope metadata: %s)", typeFamily.getFullName(), metadata())));
  }

  /**
   * Finds and converts the first annotation on this envelope matching the given converter's
   * {@link RecordConverterApi#writerTypeFamily()}.
   * @throws NoSuchElementException if no matching annotation is found
   */
  default <T extends SpecificRecordBase> T convertRequiredAnnotation(RecordConverterApi<T> converter) {
    return converter.convert(getRequiredAnnotationRecord(converter.writerTypeFamily()));
  }

  default Stream<UnpackableRecord> allRecords() {
    return MoreStreams.prepend(messageRecord(), annotationRecords().stream());
  }
}
