package io.upstartproject.avrocodec;

import org.apache.avro.Schema;
import org.apache.avro.io.DatumReader;
import org.apache.avro.specific.SpecificData;
import org.apache.avro.specific.SpecificRecordBase;

/**
 * A utility for unpacking {@link UnpackableRecord}s into code-generated {@link SpecificRecordBase} instance.
 * <p/>
 * Usually obtained via {@link AvroPublisher#recordUnpacker}.
 * <p/>
 * Instances of this class are thread-safe, and should be cached/reused for the lifetime of the process.
 */
public class SpecificRecordUnpacker<T extends SpecificRecordBase> {

  private final SpecificRecordType<T> recordType;

  public SpecificRecordUnpacker(Class<T> recordClass) {
    this(SpecificRecordType.of(recordClass));
  }

  public SpecificRecordUnpacker(SpecificRecordType<T> recordType) {
    this.recordType = recordType;
  }

  public SpecificRecordType<T> recordType() {
    return recordType;
  }

  public Class<T> getRecordClass() {
    return recordType().recordClass();
  }

  public Schema getSchema() {
    return recordType().schema();
  }

  public T unpack(UnpackableRecord record) {
    return record.read(recordType().createDatumReader());
  }
}
