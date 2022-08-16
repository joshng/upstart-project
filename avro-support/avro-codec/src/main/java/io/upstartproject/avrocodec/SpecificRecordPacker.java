package io.upstartproject.avrocodec;

import io.upstartproject.avro.PackedRecord;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.specific.SpecificDatumWriter;
import org.apache.avro.specific.SpecificRecordBase;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * A serializer for a specific code-generated avro record-type ({@link SpecificRecordBase}).
 * <p/>
 * Usually obtained from {@link AvroPublisher.RecordPacker#specificPacker(Class)}.
 * <p/>
 * Note that the specific record-type ({@link T}) must represent the same schema as the {@link AvroPublisher.RecordPacker}
 * it augments. If you wish to convert records to an alternate schema, use a {@link SpecificRecordConverter} instead.
 * <p/>
 * Instances of this class are thread-safe, and should be cached/reused for the lifetime of the process.
 *
 * @see AvroPublisher.RecordPacker#specificPacker
 */
public class SpecificRecordPacker<T extends SpecificRecordBase> implements RecordPackerApi<T> {
  private final AvroPublisher.RecordPacker genericPacker;
  private final SpecificDatumWriter<GenericRecord> writer;
  private final SpecificRecordType<T> recordType;

  /**
   * @throws IllegalArgumentException if the provided record-class does not match the provided RecordPacker's schema
   */
  SpecificRecordPacker(AvroPublisher.RecordPacker genericPacker, SpecificRecordType<T> recordType) {
    checkArgument(
            recordType.publishedSchemaDescriptor().fingerprint().equals(genericPacker.fingerprint()),
            "Mismatched schemas:\n  schema: %s\n  record: %s",
            genericPacker.schema(),
            recordType.schema()
    );
    this.recordType = recordType;
    this.genericPacker = genericPacker;
    writer = new SpecificDatumWriter<>(recordType.schema());
  }

  public PackedRecord pack(T record) {
    return genericPacker.packWithWriter(record, writer);
  }

  @Override
  public RecordTypeFamily getTypeFamily() {
    return genericPacker.getTypeFamily();
  }

  @Override
  public Schema schema() {
    return genericPacker.schema();
  }

  public Class<T> recordClass() {
    return recordType.recordClass();
  }

  public AvroPublisher.RecordPacker genericPacker() {
    return genericPacker;
  }

  @Override
  public PackableRecord<T> makePackable(T record) {
    return PackableRecord.of(record, this);
  }

  @SuppressWarnings("unchecked")
  public PackableRecord<? extends T> specificPackable(PackableRecord<?> genericPackable) {
    return recordType.recordClass().isInstance(genericPackable.record()) && genericPackable.getFingerprint().equals(fingerprint())
            ? (PackableRecord<T>) genericPackable
            : makePackable((T) recordType.specificData().deepCopy(schema(), genericPackable.record()));
  }

  @Override
  public SchemaFingerprint fingerprint() {
    return genericPacker.fingerprint();
  }
}
