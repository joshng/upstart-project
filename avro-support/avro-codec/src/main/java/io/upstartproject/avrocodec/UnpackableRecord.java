package io.upstartproject.avrocodec;

import com.google.common.collect.ImmutableList;
import io.upstartproject.avro.PackedRecord;
import upstart.util.exceptions.UncheckedIO;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.DatumReader;
import org.apache.avro.specific.SpecificDatumReader;
import org.apache.avro.specific.SpecificRecordBase;
import org.apache.avro.util.ByteBufferInputStream;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * A {@link PackedRecord} whose writer-schema has been resolved, and is thus ready to be "unpacked".
 */
public class UnpackableRecord {
  private final PackedRecord record;
  private final SchemaDescriptor writerPacker;
  private final Map<Schema, GenericRecord> unpackCache = new ConcurrentHashMap<>();

  UnpackableRecord(PackedRecord record, SchemaDescriptor writerPacker) {
    checkArgument(writerPacker.fingerprint().value() == record.getFingerprint(), "Mismatched fingerprints");
    this.record = record;
    this.writerPacker = writerPacker;
  }

  public SchemaFingerprint fingerprint() {
    return writerPacker.fingerprint();
  }

  public Schema schema() {
    return writerPacker.schema();
  }

  public PackedRecord getPackedRecord() {
    return record;
  }

  /**
   * Deserializes the record as an instance of the unpacker's target class, performing compatible schema transformations
   * as needed to adapt the data from the "writer's schema" to match the expected structure.
   * <p/>
   * Note that customizing an unpacker with a reduced (but compatible) schema is an effective way to optimize
   * deserialization when you don't need all of the fields included in the record: deserialization will be skipped
   * for any fields omitted by the schema associated with the given unpacker.
   */
  public <T extends SpecificRecordBase> T unpackWith(SpecificRecordUnpacker<T> unpacker) {
    return unpacker.unpack(this);
  }

  /**
   * Polymorphic deserializer that returns SpecificRecordBase types known to the given ClassLoader whenever possible
   * (or falls back to GenericRecord if no class is found matching the name of the packed schema)
   */
  public GenericRecord unpackSpecificOrGeneric(ClassLoader classLoader) {
    return read(new SpecificDatumReader<>(AvroPublisher.specificData(classLoader)));
  }

  /**
   * Deserializes the record using the provided readerSchema.
   * <p/>
   * Note that a customized readerSchema is an effective way to optimize deserialization
   * when you don't need all of the fields included in the record: deserialization will be skipped
   * for any fields omitted by the provided schema.
   * @param readerSchema
   */
  public GenericRecord unpackGeneric(Schema readerSchema) {
    return unpackCache.computeIfAbsent(readerSchema, s -> read(new GenericDatumReader<>(s)));
  }

  /**
   * Deserializes the enclosed record into a generic structure containing exactly what was originally written.
   */
  public GenericRecord unpackGeneric() {
    return unpackGeneric(writerPacker.schema());
  }

  /**
   * IMPORTANT: DatumReaders are mutable (see {@link DatumReader#setSchema}), so the reader
   * provided here should probably be constructed anew for each invocation
   */
  <T extends GenericRecord> T read(DatumReader<T> reader) {
    reader.setSchema(writerPacker.schema());

    return UncheckedIO.getUnchecked(() -> reader.read(null, AvroPublisher.binaryDecoder(byteBufferInputStream(record.getData()))));
  }

  private static InputStream byteBufferInputStream(ByteBuffer message) {
    return new ByteBufferInputStream(ImmutableList.of(message.duplicate()));
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof UnpackableRecord)) return false;
    UnpackableRecord that = (UnpackableRecord) o;
    return record.equals(that.record);
  }

  @Override
  public int hashCode() {
    return record.hashCode();
  }
}
