package io.upstartproject.avrocodec;

import org.apache.avro.specific.SpecificRecordBase;

import java.util.concurrent.CompletableFuture;

public interface RecordConverterApi<T extends SpecificRecordBase> {
  default CompletableFuture<T> readPackedRecord(byte[] packedRecordBytes, AvroDecoder avroCodec) {
    return avroCodec.readUnpackableRecord(packedRecordBytes).thenApply(this::convert);
  }

  T convert(UnpackableRecord record);

  RecordTypeFamily writerTypeFamily();
}
