package io.upstartproject.avrocodec;

import io.upstartproject.avro.PackedRecord;
import org.apache.avro.generic.GenericRecord;

public interface RecordPackerApi<R extends GenericRecord> extends SchemaDescriptor {
  /**
   * Provides methods for converting this record into a {@link PackedRecord}, and/or serializing it as bytes
   * @return
   */
  default PackableRecord<? extends R> makePackable(R record) {
    return PackableRecord.of(record, this);
  }

  PackedRecord pack(R record);

  RecordTypeFamily getTypeFamily();

}
