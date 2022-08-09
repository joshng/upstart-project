package io.upstartproject.avrocodec;

import com.google.common.collect.Streams;
import io.upstartproject.avro.PackedRecord;
import org.apache.avro.file.DataFileReader;
import org.apache.avro.file.DataFileStream;
import org.apache.avro.specific.SpecificDatumReader;
import org.apache.avro.specific.SpecificRecordBase;
import upstart.util.exceptions.UncheckedIO;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import static upstart.util.exceptions.Fallible.fallible;

/**
 * Reading a record that was serialized as a {@link PackedRecord} might potentially involve an asynchronous process,
 * because the {@link PackedRecord#getFingerprint fingerprint} may correspond to a newly-added schema which must first
 * be retrieved from a remote {@link SchemaRegistry} via RPC.
 * <br/>
 * Therefore, {@link #toUnpackable(PackedRecord)} returns a {@link CompletableFuture} that completes when the record's
 * schema fingerprint has been resolved, allowing the record to be {@link UnpackableRecord unpackable}.
 *
 */
@Singleton
public class AvroDecoder {

  private static final SpecificDatumReader<PackedRecord> PACKED_RECORD_READER = new SpecificDatumReader<>(PackedRecord.getClassSchema());

  private final AvroTaxonomy taxonomy;

  @Inject
  public AvroDecoder(AvroTaxonomy taxonomy) {
    this.taxonomy = taxonomy;
  }

  /**
   * Reads an avro-serialized {@link PackedRecord} from the given {@link InputStream}
   */
  public static PackedRecord readPackedRecord(InputStream in) {
    return UncheckedIO.getUnchecked(() -> PACKED_RECORD_READER.read(null, AvroPublisher.binaryDecoder(in)));
  }

  /**
   * Prepares a {@link SpecificRecordUnpacker} for deserializing compatible {@link PackedRecord}s into instances of the
   * given {@code recordClass}.
   * <p/>
   * Note that the given {@code recordClass} does NOT need to exactly match the schema of the records it unpacks
   * (or even be in the same {@link RecordTypeFamily}); it merely needs to be <em>compatible</em>:
   * <ul>
   *   <li>Fields declared by the {@code recordClass} must have types compatible with the corresponding fields in the
   *   writen record's schema</li>
   *   <li>Any fields declared by the {@code recordClass} which do not appear in the written record's schema must provide
   *   {@code default} values</li>
   * </ul>
   * Constructing use-case-centric {@link SpecificRecordBase} schemas can improve performance, by skipping deserialization
   * of undesired content.
   * <p/>
   * See https://docs.confluent.io/current/schema-registry/docs/avro.html#forward-compatibility for more about compatibility.
   */
  public <T extends SpecificRecordBase> SpecificRecordUnpacker<T> recordUnpacker(Class<T> recordClass) {
    return new SpecificRecordUnpacker<>(recordClass);
  }

  public <T extends SpecificRecordBase> SpecificRecordConverter<T> recordConverter(Class<T> recordClass) {
    return new SpecificRecordConverter<>(taxonomy.findOrCreateTypeFamily(recordClass), recordUnpacker(recordClass));
  }

  /**
   * Prepares the given data for unpacking. If necessary, resolves the given {@code fingerprint} from the
   * {@link AvroPublisher} first.
   * @return a {@link CompletableFuture} which holds the {@link UnpackableRecord}, when the {@code fingerprint} has been
   * resolved (which may require RPC to the {@link AvroPublisher} for an unrecognized fingerprint).
   */
  public CompletableFuture<UnpackableRecord> toUnpackable(long fingerprint, ByteBuffer data) {
    return toUnpackable(new PackedRecord(fingerprint,  data));
  }

  /**
   * Prepares the given {@link PackedRecord} for unpacking. If necessary, resolves the given {@link PackedRecord#getFingerprint fingerprint}
   * from the {@link AvroPublisher} first.
   * @return a {@link CompletableFuture} which holds an {@link UnpackableRecord}, when the {@code fingerprint} has been
   * resolved (which may require RPC to the {@link AvroPublisher} for an unrecognized fingerprint).
   */
  public CompletableFuture<UnpackableRecord> toUnpackable(PackedRecord record) {
    return taxonomy.findSchemaDescriptor(SchemaFingerprint.of(record.getFingerprint()))
            .thenApply(writerPacker -> new UnpackableRecord(record, writerPacker.registeredSchema()));
  }


  public CompletableFuture<UnpackableRecord> readUnpackableRecord(InputStream in) {
    return toUnpackable(readPackedRecord(in));
  }

  public CompletableFuture<UnpackableRecord> readUnpackableRecord(byte[] bytes) {
    return readUnpackableRecord(new ByteArrayInputStream(bytes));
  }

  public Stream<CompletableFuture<UnpackableRecord>> readPackedRecordFile(InputStream in) throws IOException {
    return readPackedRecords(new DataFileStream<>(in, PACKED_RECORD_READER));
  }

  public Stream<CompletableFuture<UnpackableRecord>> readPackedRecordFile(File file) throws IOException {
    return readPackedRecords(new DataFileReader<>(file, PACKED_RECORD_READER));
  }

  private <R extends Iterator<PackedRecord> & Closeable> Stream<CompletableFuture<UnpackableRecord>> readPackedRecords(R reader) {
    return Streams.stream(reader)
            .map(this::toUnpackable)
            .onClose(fallible(reader::close));
  }
}
