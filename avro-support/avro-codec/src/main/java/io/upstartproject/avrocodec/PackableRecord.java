package io.upstartproject.avrocodec;

import io.upstartproject.avro.PackedRecord;
import upstart.util.collect.Optionals;
import upstart.util.reflect.Reflect;
import org.apache.avro.generic.GenericRecord;
import org.immutables.value.Value;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Optional;

/**
 * Encapsulates a record that can be {@link RecordPackerApi#pack packed}.
 * @see RecordPackerApi#makePackable
 * @see SpecificRecordPacker
 */
@Value.Immutable
public interface PackableRecord<T extends GenericRecord> {
  static <T extends GenericRecord> PackableRecord<T> of(T record, RecordPackerApi<T> packer) {
    return PackableRecord.<T>builder().record(record).packer(packer).build();
  }

  static <T extends GenericRecord> ImmutablePackableRecord.Builder<T> builder() {
    return ImmutablePackableRecord.builder();
  }

  @Value.Auxiliary
  RecordPackerApi<T> packer();
  T record();

  PackableRecord<T> withRecord(T record);

  @Value.Lazy
  default PackedRecord packedRecord() {
    return packer().pack(record());
  }

  @Value.Derived
  @Value.Auxiliary
  default RecordTypeFamily getRecordTypeFamily() {
    return packer().getTypeFamily();
  }

  @Value.Derived
  @Value.Auxiliary
  default SchemaFingerprint getFingerprint() {
    return packer().fingerprint();
  }

  default void writeSerialized(OutputStream out) throws IOException {
    AvroPublisher.writePackedRecord(packedRecord(), out);
  }

  default <R extends GenericRecord> Optional<PackableRecord<R>> asInstance(Class<R> recordClass) {
    return Reflect.blindCast(Optionals.onlyIf(recordClass.isInstance(record()), this));
  }

  default byte[] serialize() {
    return AvroPublisher.serializePackedRecord(packedRecord());
  }
}
