package io.upstartproject.avrocodec;

import org.apache.avro.Schema;
import org.apache.avro.specific.SpecificRecordBase;

import java.util.concurrent.CompletableFuture;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * Encapsulates support for converting {@link UnpackableRecord} (packed) records from a specific {@link AvroCodec.RecordTypeFamily}
 * to a compatible specific structure.
 * <p/>
 * Usually obtained from {@link AvroCodec#recordConverter(Class)} (when the record-type ({@link T}) is a member of the target
 * {@link AvroCodec.RecordTypeFamily}, or via {@link AvroCodec.RecordTypeFamily#recordConverter(Class)} otherwise.
 * <p/>
 * Instances of this class are thread-safe, and should be cached/reused for the lifetime of the process.
 */
public class SpecificRecordConverter<T extends SpecificRecordBase> implements RecordConverterApi<T> {
  private final AvroCodec.RecordTypeFamily writerTypeFamily;
  private final SpecificRecordUnpacker<T> unpacker;

  protected SpecificRecordConverter(AvroCodec.RecordTypeFamily writerTypeFamily, SpecificRecordUnpacker<T> unpacker) {
    checkArgument(writerTypeFamily.isCompatibleReader(unpacker.getSchema()));
    this.writerTypeFamily = writerTypeFamily;
    this.unpacker = unpacker;
  }

  @Override
  public T convert(UnpackableRecord record) {
    return unpacker.unpack(record);
  }

  @Override
  public AvroCodec.RecordTypeFamily writerTypeFamily() {
    return writerTypeFamily;
  }

  public Class<T> recordClass() {
    return unpacker.getRecordClass();
  }

  public Schema schema() {
    return unpacker.getSchema();
  }
}
