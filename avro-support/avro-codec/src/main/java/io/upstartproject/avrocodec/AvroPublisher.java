package io.upstartproject.avrocodec;

import com.google.common.annotations.Beta;
import com.google.common.base.MoreObjects;
import com.google.common.base.Supplier;
import com.google.common.base.Throwables;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableList;
import io.upstartproject.avro.PackedRecord;
import upstart.util.annotations.Tuple;
import upstart.util.concurrent.CompletableFutures;
import upstart.util.concurrent.Promise;
import upstart.util.concurrent.ShutdownException;
import upstart.util.exceptions.UncheckedIO;
import org.apache.avro.Schema;
import org.apache.avro.SchemaCompatibility;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.generic.GenericDatumWriter;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.BinaryDecoder;
import org.apache.avro.io.BinaryEncoder;
import org.apache.avro.io.DatumWriter;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.io.Encoder;
import org.apache.avro.io.EncoderFactory;
import org.apache.avro.specific.SpecificData;
import org.apache.avro.specific.SpecificDatumWriter;
import org.apache.avro.specific.SpecificRecordBase;
import org.immutables.value.Value;
import org.reflections.Reflections;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Singleton;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

/**
 * Provides support for {@link #ensureRegistered registering} schemas with the configured {@link SchemaRegistry},
 * as well as utilities for writing ("packing") and reading ("unpacking") avro records into and out of {@link PackedRecord}
 * instances.
 * <p/>
 * Instances of this class are thread-safe, and should be cached/reused for the lifetime of the process.
 * <p/>
 * The schemas and {@link PackedRecord#getFingerprint fingerprints} found in records processed by this class are required
 * to have been {@link #ensureRegistered registered} with the underlying {@link SchemaRegistry} before working with them.
 * <p/>
 * <h2>Writing Records</h2>
 * Usually, a process that will write {@link PackedRecord} data out-of-process should register all emitted schemas
 * at startup (via {@link #ensureRegistered} or {@link #registerSpecificRecordSchemas}), and then obtain
 * {@link RecordPacker} instances for each schema via {@link #getPreRegisteredPacker} (and then {@link RecordPacker#specificPacker})
 * after registration has completed. Note that Avro's generated {@link SpecificRecordBase} subclasses offer a static {@code getClassSchema()} method
 * (eg, {@link PackedRecord#getClassSchema}), which should be presented to {@link #ensureRegistered}
 * (or {@link #getOrRegisterPacker(Schema)}).
 *
 * <h3>Schema Compatibility</h3>
 * Note that schema compatibility-checks are performed for each schema which is registered: every schema which is newly
 * added to the {@link SchemaRegistry} must be forward- and backward-compatible with any schema that was previously
 * registered with the same {@link Schema#getFullName full-name} (ie, package + name).
 * <p/>
 * If a proposed schema fails this validation (because another conflicting schema was already registered with the same name),
 * the {@link CompletableFuture}s returned from {@link #getPreRegisteredPacker(Schema)} (SchemaFingerprint)}
 * or {@link #getOrRegisterPacker(Schema)} will complete exceptionally with a {@link AvroSchemaConflictException} describing
 * the problem.
 *
 * <h2>Reading Records</h2>
 *
 * See {@link AvroDecoder} for a description of the process for reading records.
 *
 * @see EnvelopeCodec
 * @see UnpackableRecord
 * @see SchemaNormalization2
 * @see SchemaCompatibility
 * @see RecordTypeFamily
 * @see AvroSchemaConflictException
 */
@Singleton
public class AvroPublisher {
  public static final DecoderFactory DECODER_FACTORY = DecoderFactory.get();
  private final Logger LOG = LoggerFactory.getLogger(getClass());
  private static final DatumWriter<PackedRecord> PACKED_RECORD_WRITER = new SpecificDatumWriter<>(PackedRecord.getClassSchema());
  private static final String SCHEMA_PUBLISHED_PROPERTY = "published";

  private final Queue<SchemaDescriptor> pendingRegistrations = new ConcurrentLinkedQueue<>();

  private final LoadingCache<SchemaDescriptor, CompletableFuture<RecordPacker>> knownPackersBySchema = CacheBuilder.newBuilder()
          .build(CacheLoader.from(this::register));

  private final LoadingCache<SchemaFingerprint, Promise<RecordPacker>> knownPackersByFingerprint = CacheBuilder.newBuilder()
          .build(CacheLoader.from((Supplier<Promise<RecordPacker>>) Promise::new));

  private final AvroTaxonomy taxonomy;

  /**
   * Constructs an AvroCodec backed by the given {@link SchemaRegistry}
   */
  public AvroPublisher(AvroTaxonomy taxonomy) {
    this.taxonomy = taxonomy;
    taxonomy.setListener(new AvroTaxonomy.TaxonomyListener() {
      @Override
      public void onSchemaAdded(SchemaDescriptor schema, RecordTypeFamily.RegistrationResult registrationResult) {
        AvroPublisher.this.onSchemaAdded(schema, registrationResult);
      }

      @Override
      public void onSchemaRemoved(SchemaFingerprint fingerprint) {
        AvroPublisher.this.onSchemaRemoved(fingerprint);
      }

      @Override
      public void onShutdown() {
        knownPackersByFingerprint.asMap().forEach((fingerprint, promise) -> {
          if (!promise.isDone()) promise.completeExceptionally(new ShutdownException("AvroCodec was shut down while awaiting schema: " + fingerprint.hexValue()));
        });
      }
    });
  }

  public static boolean isMarkedForPublication(Schema schema) {
    Object publishedProp = schema.getObjectProp(SCHEMA_PUBLISHED_PROPERTY);
    if (publishedProp instanceof Boolean) {
      return (Boolean) publishedProp;
    } else if (publishedProp == null) {
      return false;
    } else {
      throw new IllegalArgumentException(String.format("Unsupported \"%s\" value in schema (should be a boolean) for %s: %s", SCHEMA_PUBLISHED_PROPERTY, schema.getFullName(), publishedProp));
    }
  }

  /**
   * Serializes the PackedRecord as an avro byte array. Using this method directly is rarely necessary;
   * {@link RecordPacker#makePackable(GenericRecord)}.{@link PackableRecord#serialize() serialize()} is usually preferable.
   * @see RecordPacker#makePackable
   * @see PackableRecord#serialize
   */
  public static byte[] serializePackedRecord(PackedRecord record) {
    return UncheckedIO.captureBytes(RecordPacker.INITIAL_BUFSIZE, out -> writePackedRecord(record, out));
  }

  /**
   * Writes the PackedRecord as an avro byte array to the given output. Using this method directly is rarely necessary;
   * {@link RecordPacker#makePackable(GenericRecord)}.{@link PackableRecord#writeSerialized writeSerialized(out)}
   * is usually preferable.
   * @see RecordPacker#makePackable
   * @see PackableRecord#writeSerialized
   */
  public static void writePackedRecord(PackedRecord record, OutputStream out) throws IOException {
    Encoder encoder = encoder(out);
    PACKED_RECORD_WRITER.write(record, encoder);
    encoder.flush();
  }

  private static BinaryEncoder encoder(OutputStream out) {
    return EncoderFactory.get().binaryEncoder(out, null);
  }


  @Beta
  public CompletableFuture<GenericRecord> convertFromJson(String typeName, InputStream json) {
    return taxonomy.refresh().thenApply(__ -> UncheckedIO.getUnchecked(() -> {
      Schema schema = taxonomy.findTypeFamily(typeName).requireLatestSchema().schema();
      return new GenericDatumReader<GenericRecord>(schema).read(null, DECODER_FACTORY.jsonDecoder(schema, json));
    }));
  }


  public CompletableFuture<List<SpecificRecordPacker<?>>> registerSpecificPackers(PackageKey packageKey) {
    return registerSpecificRecordSchemas(packageKey)
            .thenApply(schemas -> schemas.stream().map(this::getPreRegisteredPacker)
                    .collect(Collectors.toList())
            );
  }

  public CompletableFuture<List<SpecificRecordType<?>>> registerSpecificRecordSchemas(PackageKey packageKey) {

    List<SpecificRecordType<?>> publishedTypes = packageKey.findPublishedTypes();

    return ensureRegistered(publishedTypes.stream()).thenApply(ignored -> publishedTypes);
  }

  public CompletableFuture<Void> ensureRegistered(Stream<SpecificRecordType<?>> schemas) {
    return ensureRegistration(schemas.map(SpecificRecordType::publishedSchemaDescriptor));
  }

  @SafeVarargs
  public final CompletableFuture<Void> ensureRegistered(Class<? extends SpecificRecordBase>... schemas) {
    return ensureRegistered(Arrays.stream(schemas).map(SpecificRecordType::of));
  }

  public CompletableFuture<Void> ensureRegistered(Schema... schemas) {
    return ensureRegistration(Arrays.stream(schemas).map(SchemaDescriptor::of));
  }

  private CompletableFuture<Void> ensureRegistration(Stream<SchemaDescriptor> schemas) {

    CompletableFuture<Void> result = CompletableFutures.allOf(schemas.sequential().map(knownPackersBySchema::getUnchecked));

    List<SchemaDescriptor> newSchemas = null;
    SchemaDescriptor pendingRegistration;
    while ((pendingRegistration = pendingRegistrations.poll()) != null) {
      if (newSchemas == null) newSchemas = new ArrayList<>();
      newSchemas.add(pendingRegistration);
    }

    if (newSchemas != null) insert(newSchemas);

    return result;
  }

  private void insert(List<SchemaDescriptor> newSchemas) {
    taxonomy.insert(newSchemas)
            .whenComplete((__, e) -> {
              if (e != null) {
                for (SchemaDescriptor newSchema : newSchemas) {
                  knownPackersByFingerprint.getUnchecked(newSchema.fingerprint()).completeExceptionally(e);
                }
              }
            });
  }


  /**
   * Gets the {@link RecordPacker} for the provided schema, which is required to have been registered beforehand.
   *
   * @throws IllegalStateException if the provided schema was not previously registered
   * @throws AvroSchemaConflictException if the provided schema conflicted with another registered schema in the {@link SchemaRegistry}
   */
  public RecordPacker getPreRegisteredPacker(Schema schema) {
    CompletableFuture<RecordPacker> future = knownPackersBySchema.getIfPresent(SchemaDescriptor.of(schema));
    if (future == null) {
      checkPublished(schema);
      throw new IllegalArgumentException("Schema was not registered: " + schema.getFullName());
    }
    checkState(future.isDone(), "Schema registration had not completed: %s", schema.getFullName());
    try {
      return future.join();
    } catch (CompletionException e) {
      Throwable cause = MoreObjects.firstNonNull(e.getCause(), e);
      Throwables.throwIfUnchecked(cause);
      throw e;
    }
  }

  private static void checkPublished(Schema schema) {
    checkArgument(isMarkedForPublication(schema), "Schema cannot be registered because it is not marked for publication (published schemas must declare '\"%s\": true'): %s", SCHEMA_PUBLISHED_PROPERTY, schema.getFullName());
  }

  /**
   * Prepares a {@link SpecificRecordPacker} for the given code-generated record-type, whose schema must have been
   * registered beforehand.
   * <p/>
   * Note that it is slightly more efficient to use {@link #getPreRegisteredPacker(Schema)
   * getPreRegisteredPacker(MyRecord.getClassSchema())} if you have non-reflective access to the generated {@code getClassSchema}
   * method itself.
   *
   * @throws IllegalStateException if the provided schema was not previously registered
   * @throws AvroSchemaConflictException if the provided schema conflicted with another registered schema in the {@link SchemaRegistry}
   */
  public <T extends SpecificRecordBase> SpecificRecordPacker<T> getPreRegisteredPacker(Class<T> recordClass) {
    return getPreRegisteredPacker(SpecificRecordType.of(recordClass));
  }

  public <T extends SpecificRecordBase> SpecificRecordPacker<T> getPreRegisteredPacker(SpecificRecordType<T> recordType) {
    return getPreRegisteredPacker(recordType.schema()).specificPacker(recordType);
  }


  /**
   * Prepares a {@link AvroPublisher.RecordPacker} for the given {@link Schema}, registering the schema with the {@link AvroPublisher}
   * if necessary.
   * @param schema
   * @return a {@link CompletableFuture} which holds a {@link AvroPublisher.RecordPacker}, which completes only when the
   * {@code fingerprint} has been successfully registered with the {@link SchemaRegistry}.
   * @throws AvroSchemaConflictException (via the returned {@link CompletableFuture}) if the provided schema
   * conflicted with another registered schema in the {@link SchemaRegistry}
   */
  public CompletableFuture<RecordPacker> getOrRegisterPacker(Schema schema) {
    SchemaDescriptor descriptor = SchemaDescriptor.of(schema);
    return ensureRegistration(Stream.of(descriptor))
            .thenCompose(__ -> knownPackersBySchema.getUnchecked(descriptor));
  }

//  /**
//   * Finds the registered {@link RecordPacker} for the given {@link SchemaFingerprint}, returning a CompleteableFuture
//   * which waits (indefinitely!) for the schema to be published by the the underlying {@link SchemaRegistry} if necessary.
//   * @return a {@link CompletableFuture} which completes when the fingerprint has been resolved (which should usually be
//   * immediate, but may delay indefinitely if the corresponding schema is never published by the SchemaRepo).
//   * @throws IllegalStateException if the {@link AvroCodec} is not {@link #isRunning running} (either because it has
//   * not been {@link #startAsync started}, has been {@link #stopAsync stopped}, or because it has encountered an
//   * unrecoverable error accessing the SchemaRepo)
//   */
//  public CompletableFuture<RecordPacker> findRegisteredPacker(SchemaFingerprint fingerprint) {
//    checkRunning();
//    Promise<RecordPacker> packerFuture = knownPackersByFingerprint.getUnchecked(fingerprint);
//    if (!packerFuture.isDone()) LOG.warn("Awaiting arrival of unrecognized schema-fingerprint: {}", fingerprint.hexValue());
//    return packerFuture;
//  }

  /**
   * Finds the registered {@link RecordPacker} for the given {@link SchemaFingerprint}, syncing with the underlying
   * {@link SchemaRegistry} if necessary.
   * @return a {@link CompletableFuture} which completes when the fingerprint has been resolved (which should usually be
   * immediate).
   * <p/>
   * Note that this method requires the requested fingerprint to be <em>immediately</em> accessible via the {@link SchemaRegistry}.
   *
   * @throws IllegalStateException if the {@link AvroTaxonomy} is not {@link AvroTaxonomy#isRunning running} (either because it has
   * not been {@link AvroTaxonomy#start started} or because it has encountered an unrecoverable error accessing the SchemaRegistry)
   * @throws IllegalStateException via the returned {@link CompletableFuture} if the {@code fingerprint} could not be
   * resolved with the refreshed contents of the {@link SchemaRegistry}.
   */
  public CompletableFuture<RecordPacker> findPreRegisteredPacker(SchemaFingerprint fingerprint) {
    return Optional.<CompletableFuture<RecordPacker>>ofNullable(knownPackersByFingerprint.getIfPresent(fingerprint))
            .orElseGet(() -> taxonomy.refresh()
                    .thenCompose(__ -> {
                      Promise<RecordPacker> found = knownPackersByFingerprint.getIfPresent(fingerprint);
                      checkState(found != null, "Unrecognized schema", fingerprint);
                      return found;
                    })
            );
  }

  public CompletableFuture<List<SchemaDescriptor>> getAllRegisteredSchemas(boolean refresh) {
    CompletableFuture<?> refreshed = refresh ? taxonomy.refresh() : CompletableFutures.nullFuture();

    return refreshed.thenApply(ignored -> taxonomy.getAllRegisteredRecordTypes()
            .flatMap(RecordTypeFamily::getAllVersions)
            .toList()
    );
  }

  private void onSchemaAdded(SchemaDescriptor descriptor, RecordTypeFamily.RegistrationResult registrationResult) {
    Promise<RecordPacker> promise = knownPackersByFingerprint.getUnchecked(descriptor.fingerprint());
    if (promise.isDone()) return; // we tolerate multiple copies of the same schema

    if (registrationResult.succeeded()) {
      LOG.info("Loaded schema from repository: {}", descriptor);
      promise.complete(new RecordPacker(registrationResult.registeredSchema(), registrationResult.typeFamily()));
    } else {
      AvroSchemaConflictException ex = registrationResult.getConflictException();
      LOG.warn("Rejected schema that was speculatively added to the repo: {}", ex.getMessage());
      promise.completeExceptionally(ex);
    }
  }

  private void onSchemaRemoved(SchemaFingerprint fingerprint) {
    Promise<RecordPacker> promise = knownPackersByFingerprint.getIfPresent(fingerprint);
    if (promise != null && !promise.isCompletedExceptionally()) {
      LOG.warn("Schema was removed from repo, but promise hadn't completed exceptionally. Probably shouldn't happen..? {}", fingerprint);
      promise.obtrudeException(new IllegalStateException("Schema was removed from the SchemaRepo: " + fingerprint));
    }
  }

  private CompletableFuture<RecordPacker> register(SchemaDescriptor descriptor) {
    RecordTypeFamily family = taxonomy.findOrCreateTypeFamily(descriptor.fullName());
    Optional<AvroSchemaConflictException> exception = family.checkCompatibility(descriptor);
    return exception.<CompletableFuture<RecordPacker>>map(CompletableFutures::failedFuture)
            .orElseGet(() -> {
              Promise<RecordPacker> promise = knownPackersByFingerprint.getUnchecked(descriptor.fingerprint());
              if (!promise.isDone()) { // TODO should this somehow check if the task has been STARTED, rather than done?
                pendingRegistrations.offer(descriptor);
                return CompletableFutures.recoverCompose(promise, AvroSchemaConflictException.class, conflictException ->
                        CompletableFutures.sequence(taxonomy.delete(descriptor).handle((__, e2) -> {
                          if (e2 != null) {
                            LOG.warn("Error deleting conflicting schema", e2);
                            conflictException.addSuppressed(e2);
                          }
                          return promise; // amusingly, we can just return the failed promise here
                        })));
              } else {
                return promise;
              }
            });
  }




  static BinaryDecoder binaryDecoder(InputStream in) {
    return DECODER_FACTORY.binaryDecoder(in, null);
  }

  public CompletableFuture<Void> ensureReplicatedFrom(AvroPublisher other) {
    checkArgument(this != other, "Tried to replicate identical repo");
    return other.getAllRegisteredSchemas(true).thenApply(Collection::stream).thenCompose(this::ensureRegistration);
  }

  public static PackageKey packageKey(Class<? extends SpecificRecordBase> classFromPackage) {
    return PackageKey.fromRecordPackage(classFromPackage);
  }

  public static PackageKey packageKey(String packageName, ClassLoader classLoader) {
    return PackageKey.of(packageName, classLoader);
  }
  /**
   * Encapsulates a registered writer-schema: provides the ability to {@link #pack} avro records for transmission.
   * <p/>
   * Note that if you are working with generated {@link SpecificRecordBase} classes, it may be good to use a
   * {@link #specificPacker}, which is type-safe and slightly optimized.
   * <p/>
   * Instances of this class are obtained from {@link AvroPublisher#getPreRegisteredPacker} or
   * {@link AvroPublisher#getOrRegisterPacker}, and are thread-safe and reusable. For example, do something like this
   * when starting up for every record-type you might serialize for the lifetime of the application:
   * <pre>
   * {@code
   *   avroCodec.registerPackageSpecificRecordSchemas(
   *       "my.package.name",
   *       MySpecificRecordClass.class.getClassLoader()
   *   ).join(); // register all SpecificRecordBase schemas under "my.package.name"
   *
   *   RecordPacker genericPacker = avroCodec.getPreRegisteredPacker(
   *           MySpecificRecord.getClassSchema()      // note: getClassSchema() to retrieve SpecificRecordBase schemas
   *   );
   *
   *   SpecificRecordPacker<MySpecificRecord> myPacker = genericPacker.specificPacker(MySpecificRecord.class);
   * }
   * </pre>
   */
  public static class RecordPacker implements RecordPackerApi<GenericRecord> {

    static final int INITIAL_BUFSIZE = 512;
    private final SchemaFingerprint fingerprint;
    private final Schema schema;
    private final DatumWriter<GenericRecord> genericWriter;
    private final RecordTypeFamily typeFamily;

    private RecordPacker(SchemaDescriptor schemaDescriptor, RecordTypeFamily typeFamily) {
      this.fingerprint = schemaDescriptor.fingerprint();
      this.schema = schemaDescriptor.schema();
      this.genericWriter = new GenericDatumWriter<>(schema);
      this.typeFamily = typeFamily;
    }

    public RecordTypeFamily getTypeFamily() {
      return typeFamily;
    }

    @Override
    public PackableRecord<? extends GenericRecord> makePackable(GenericRecord record) {
      return record instanceof SpecificRecordBase specificRecord
              ? makePackable(specificRecord)
              : RecordPackerApi.super.makePackable(record);
    }

    @SuppressWarnings("unchecked")
    public <R extends SpecificRecordBase> PackableRecord<R> makePackable(R specificRecord) {
      return PackableRecord.of(
              specificRecord,
              specificPacker(SpecificRecordType.of((Class<R>)specificRecord.getClass()))
      );
    }

    @Override
    public PackedRecord pack(GenericRecord record) {
      DatumWriter<GenericRecord> writer;
      if (record instanceof SpecificRecordBase) {
        writer = new SpecificDatumWriter<>(record.getSchema());
      } else {
        writer = genericWriter;
      }
      return packWithWriter(record, writer);
    }

    public <T extends SpecificRecordBase> SpecificRecordPacker<T> specificPacker(SpecificRecordType<T> recordType) {
      return new SpecificRecordPacker<>(this, recordType);
    }

    public SchemaFingerprint fingerprint() {
      return fingerprint;
    }

    public Schema schema() {
      return schema;
    }

    <T extends GenericRecord> PackedRecord packWithWriter(T record, DatumWriter<T> writer) {
      checkArgument(SchemaFingerprint.of(record.getSchema()).equals(fingerprint), "Mismatched record-schema");
      ByteBuffer data = ByteBuffer.wrap(UncheckedIO.captureBytes(INITIAL_BUFSIZE,
              byteStream -> writer.write(record, EncoderFactory.get().directBinaryEncoder(byteStream, null))));

      return new PackedRecord(fingerprint().value(), data);
    }

    @Override
    public String toString() {
      return "RecordPacker{" +
              "fingerprint=" + fingerprint +
              ", typeFamily=" + typeFamily.getFullName() +
              '}';
    }
  }

  @Value.Immutable(intern = true)
  @Tuple
  public interface PackageKey {
    static PackageKey of(String packageName, ClassLoader classLoader) {
      return ImmutablePackageKey.of(packageName, classLoader);
    }

    static PackageKey fromRecordPackage(Class<? extends SpecificRecordBase> exampleClass) {
      return of(exampleClass.getPackageName(), exampleClass.getClassLoader());
    }

    String packageName();

    ClassLoader classLoader();

    @Value.Lazy
    default List<SpecificRecordType<?>> findPublishedTypes() {
      Set<Class<? extends SpecificRecordBase>> recordClasses;
      // TODO: Reflections is not thread-safe ... consider switching to ClassGraph for this https://github.com/classgraph/classgraph
      synchronized (Reflections.class) {
        recordClasses = new Reflections(
                packageName(),
                classLoader()
        ).getSubTypesOf(SpecificRecordBase.class);
      }

      return recordClasses.stream()
              .map(SpecificRecordType::of)
              .filter(type -> isMarkedForPublication(type.schema()))
              .collect(ImmutableList.toImmutableList());
    }
  }
}
