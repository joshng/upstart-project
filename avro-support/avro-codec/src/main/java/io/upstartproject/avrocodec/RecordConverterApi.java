package io.upstartproject.avrocodec;

import org.apache.avro.specific.SpecificRecordBase;

public interface RecordConverterApi<T extends SpecificRecordBase> {
  T convert(UnpackableRecord record);

  AvroCodec.RecordTypeFamily writerTypeFamily();
}
