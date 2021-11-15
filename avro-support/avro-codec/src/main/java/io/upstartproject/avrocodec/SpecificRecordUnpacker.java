package io.upstartproject.avrocodec;

import org.apache.avro.Schema;
import org.apache.avro.io.DatumReader;
import org.apache.avro.specific.SpecificData;
import org.apache.avro.specific.SpecificRecordBase;

/**
 * A utility for unpacking {@link UnpackableRecord}s into code-generated {@link SpecificRecordBase} instance.
 * <p/>
 * Usually obtained via {@link AvroCodec#recordUnpacker}.
 * <p/>
 * Instances of this class are thread-safe, and should be cached/reused for the lifetime of the process.
 */
public class SpecificRecordUnpacker<T extends SpecificRecordBase> {
  private final Class<T> recordClass;
  private final Schema schema;
  private final SpecificData specificData;

  public SpecificRecordUnpacker(Class<T> recordClass) {
    this.recordClass = recordClass;
    this.specificData = AvroCodec.specificData(recordClass.getClassLoader());
    this.schema = specificData.getSchema(recordClass);
  }

  public Class<T> getRecordClass() {
    return recordClass;
  }

  public Schema getSchema() {
    return schema;
  }

  @SuppressWarnings("unchecked")
  public T unpack(UnpackableRecord record) {
    return record.read((DatumReader<T>)specificData.createDatumReader(schema));
  }
}
