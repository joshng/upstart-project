package io.upstartproject.avrocodec;

import io.upstartproject.avro.PackedRecord;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.specific.SpecificData;
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
 * @see AvroPublisher.RecordPacker#specificPacker(Class)
 */
public class SpecificRecordPacker<T extends SpecificRecordBase> implements RecordPackerApi<T> {
  private final AvroPublisher.RecordPacker genericPacker;
  private final SpecificDatumWriter<GenericRecord> writer;
  private final Class<T> recordClass;
  private final SpecificData specificData;

  /**
   * @throws IllegalArgumentException if the provided record-class does not match the provided RecordPacker's schema
   */
  public SpecificRecordPacker(AvroPublisher.RecordPacker genericPacker, Class<T> recordClass) {
    this.recordClass = recordClass;
    this.specificData = AvroPublisher.specificData(recordClass.getClassLoader());
    Schema recordSchema = specificData.getSchema(recordClass);
    checkArgument(SchemaFingerprint.of(recordSchema).equals(genericPacker.fingerprint()), "Mismatched schemas:\n  schema: %s\n  record: %s", genericPacker.schema(), recordSchema);
    this.genericPacker = genericPacker;
    writer = new SpecificDatumWriter<>(genericPacker.schema());
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
    return recordClass;
  }

  public AvroPublisher.RecordPacker genericPacker() {
    return genericPacker;
  }

  public SpecificData specificData() {
    return specificData;
  }

  @Override
  public PackableRecord<T> makePackable(T record) {
    return PackableRecord.of(record, this);
  }

  @SuppressWarnings("unchecked")
  public PackableRecord<? extends T> specificPackable(PackableRecord<?> genericPackable) {
    return recordClass.isInstance(genericPackable.record()) && genericPackable.getFingerprint().equals(fingerprint())
            ? (PackableRecord<T>) genericPackable
            : makePackable((T) specificData.deepCopy(schema(), genericPackable.record()));
  }

  @Override
  public SchemaFingerprint fingerprint() {
    return genericPacker.fingerprint();
  }
}
