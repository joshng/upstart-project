package io.upstartproject.avrocodec;

import com.google.common.annotations.Beta;
import com.google.common.base.MoreObjects;
import com.google.common.base.Throwables;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Streams;
import com.google.common.util.concurrent.AbstractService;
import io.upstartproject.avro.PackedRecord;
import upstart.util.collect.Optionals;
import upstart.util.collect.PairStream;
import upstart.util.annotations.Identifier;
import upstart.util.annotations.Tuple;
import upstart.util.concurrent.CompletableFutures;
import upstart.util.concurrent.Promise;
import upstart.util.exceptions.UncheckedIO;
import org.apache.avro.Schema;
import org.apache.avro.SchemaCompatibility;
import org.apache.avro.file.DataFileReader;
import org.apache.avro.file.DataFileStream;
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
import org.apache.avro.specific.SpecificDatumReader;
import org.apache.avro.specific.SpecificDatumWriter;
import org.apache.avro.specific.SpecificRecordBase;
import org.immutables.value.Value;
import org.reflections.Reflections;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static upstart.util.exceptions.Fallible.fallible;

/**
 * Provides support for {@link #ensureRegistered registering} schemas with the configured {@link SchemaRepo},
 * as well as utilities for writing ("packing") and reading ("unpacking") avro records into and out of {@link PackedRecord}
 * instances.
 * <p/>
 * Instances of this class are thread-safe, and should be cached/reused for the lifetime of the process. Each instance must
 * be started with {@link #startAsync}, and the startup should be confirmed via {@link #awaitRunning} (or {@link #addListener})
 * before further interactions. Similarly, instances should be {@link #stopAsync stopped} at system shutdown.
 * <p/>
 * The schemas and {@link PackedRecord#getFingerprint fingerprints} found in records processed by this class are required
 * to have been {@link #ensureRegistered registered} with the underlying {@link SchemaRepo} before working with them.
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
 * added to the {@link SchemaRepo} must be forward- and backward-compatible with any schema that was previously
 * registered with the same {@link Schema#getFullName full-name} (ie, package + name).
 * <p/>
 * If a proposed schema fails this validation (because another conflicting schema was already registered with the same name),
 * the {@link CompletableFuture}s returned from {@link #getPreRegisteredPacker(Schema)} (SchemaFingerprint)}
 * or {@link #getOrRegisterPacker(Schema)} will complete exceptionally with a {@link SchemaConflictException} describing
 * the problem.
 *
 * <h2>Reading Records</h2>
 *
 * Reading a record that was serialized as a {@link PackedRecord} might potentially involve an asynchronous process,
 * because the {@link PackedRecord#getFingerprint fingerprint} may correspond to a newly-added schema which must first
 * be retrieved from a remote {@link SchemaRepo} via RPC.
 * <br/>
 * Therefore, {@link #toUnpackable(PackedRecord)} returns a {@link CompletableFuture} that completes when the record's
 * schema fingerprint has been resolved, allowing the record to be {@link UnpackableRecord unpackable}.
 *
 * @see EnvelopeCodec
 * @see #recordUnpacker
 * @see UnpackableRecord
 * @see SchemaNormalization2
 * @see SchemaCompatibility
 * @see RecordTypeFamily
 * @see SchemaConflictException
 */
public class AvroCodec extends AbstractService {
  private final Logger LOG = LoggerFactory.getLogger(getClass());
  private static final DatumWriter<PackedRecord> PACKED_RECORD_WRITER = new SpecificDatumWriter<>(PackedRecord.getClassSchema());
  private static final SpecificDatumReader<PackedRecord> PACKED_RECORD_READER = new SpecificDatumReader<>(PackedRecord.getClassSchema());
  private static final String SCHEMA_PUBLISHED_PROPERTY = "published";

  private static final LoadingCache<ClassLoader, SpecificData> SPECIFIC_DATA_BY_CLASSLOADER = CacheBuilder.newBuilder()
          .build(new CacheLoader<ClassLoader, SpecificData>() {
            @Override
            public SpecificData load(ClassLoader classLoader) {
              return new SpecificData(classLoader);
            }
          });

  private static final LoadingCache<PackageKey, Set<Class<? extends SpecificRecordBase>>> CLASS_REFLECTION_CACHE = CacheBuilder.newBuilder()
          .build(new CacheLoader<PackageKey, Set<Class<? extends SpecificRecordBase>>>() {
            @Override
            public Set<Class<? extends SpecificRecordBase>> load(PackageKey key) {
              // TODO: Reflections is not thread-safe ... consider switching to ClassGraph for this https://github.com/classgraph/classgraph
              synchronized (AvroCodec.class) {
                return new Reflections(key.packageName(), key.classLoader()).getSubTypesOf(SpecificRecordBase.class);
              }
            }
          });

  private final Queue<SchemaDescriptor> pendingRegistrations = new ConcurrentLinkedQueue<>();

  private final LoadingCache<SchemaDescriptor, CompletableFuture<RecordPacker>> knownPackersBySchema = CacheBuilder.newBuilder()
          .build(CacheLoader.from(this::register));

  private final LoadingCache<SchemaFingerprint, Promise<RecordPacker>> knownPackersByFingerprint = CacheBuilder.newBuilder().build(CacheLoader.from(Promise::new));

  private final LoadingCache<String, RecordTypeFamily> typesByFullName = CacheBuilder.newBuilder()
          .build(CacheLoader.from(RecordTypeFamily::new));

  private final SchemaRepo schemaRepo;

  /**
   * Constructs an AvroCodec backed by the given {@link SchemaRepo}
   */
  public AvroCodec(SchemaRepo schemaRepo) {
    this.schemaRepo = schemaRepo;
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

  static SpecificData specificData(ClassLoader classLoader) {
    return SPECIFIC_DATA_BY_CLASSLOADER.getUnchecked(classLoader);
  }

  /**
   * Retrieves the schema for a code-generated avro type. Uses various layers of caching and <em>reflection</em>
   * to find the static {@code SCHEMA$} member embedded in the class by the code-generator.
   * <p/>
   * Note that if you know the specific recordClass (ie, your code can refer to {@code YourRecordClass.class}, without
   * a generic {@code Class<? extends SpecificRecordBase>} variable), then it's better to use the generated
   * {@code YourRecordClass.getClassSchema()} method instead.
   */
  public static Schema getSchema(Class<? extends SpecificRecordBase> recordClass) {
    return specificData(recordClass.getClassLoader()).getSchema(recordClass);
  }

  /**
   * Reads an avro-serialized {@link PackedRecord} from the given {@link InputStream}
   */
  public static PackedRecord readPackedRecord(InputStream in) {
    return UncheckedIO.getUnchecked(() -> PACKED_RECORD_READER.read(null, binaryDecoder(in)));
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

  /**
   * Prepares the given data for unpacking. If necessary, resolves the given {@code fingerprint} from the
   * {@link AvroCodec} first.
   * @return a {@link CompletableFuture} which holds the {@link UnpackableRecord}, when the {@code fingerprint} has been
   * resolved (which may require RPC to the {@link AvroCodec} for an unrecognized fingerprint).
   */
  public CompletableFuture<UnpackableRecord> toUnpackable(long fingerprint, ByteBuffer data) {
    return toUnpackable(new PackedRecord(fingerprint,  data));
  }

  /**
   * Prepares the given {@link PackedRecord} for unpacking. If necessary, resolves the given {@link PackedRecord#getFingerprint fingerprint}
   * from the {@link AvroCodec} first.
   * @return a {@link CompletableFuture} which holds an {@link UnpackableRecord}, when the {@code fingerprint} has been
   * resolved (which may require RPC to the {@link AvroCodec} for an unrecognized fingerprint).
   */
  public CompletableFuture<UnpackableRecord> toUnpackable(PackedRecord record) {
    return findRegisteredPacker(SchemaFingerprint.of(record.getFingerprint()))
            .thenApply(writerPacker -> new UnpackableRecord(record, writerPacker));
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

  @Beta
  public CompletableFuture<GenericRecord> convertFromJson(String typeName, InputStream json) {
    return schemaRepo.refresh().thenApply(__ -> UncheckedIO.getUnchecked(() -> {
      Schema schema = findTypeFamily(typeName).requireLatestSchema().schema();
      return new GenericDatumReader<GenericRecord>(schema).read(null, DecoderFactory.get().jsonDecoder(schema, json));
    }));
  }

  /**
   * Prepares a {@link SpecificRecordUnpacker} for deserializing compatible {@link PackedRecord}s into instances of the
   * given {@code recordClass}.
   * <p/>
   * Note that the given {@code recordClass} does NOT need to exactly match the schema of the records it unpacks
   * (or even be in the same {@link AvroCodec.RecordTypeFamily}); it merely needs to be <em>compatible</em>:
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
    return new SpecificRecordConverter<>(findTypeFamily(recordClass), recordUnpacker(recordClass));
  }

  public CompletableFuture<List<SpecificRecordPacker<?>>> registerSpecificPackers(PackageKey packageKey) {
    return registerSpecificRecordSchemas(packageKey)
            .thenApply(schemas -> schemas.stream().map(this::getPreRegisteredPacker)
                    .map(packer -> packer.inferSpecificPacker(packageKey.classLoader()))
                    .collect(Collectors.toList())
            );
  }

  public CompletableFuture<List<Schema>> registerSpecificRecordSchemas(PackageKey packageKey) {
    checkRunning();

    List<Schema> publishedSchemas = packageKey.findPublishedSchemas();

    return ensureRegistered(publishedSchemas.stream()).thenApply(ignored -> publishedSchemas);
  }

  public static Set<Class<? extends SpecificRecordBase>> findRecordClasses(PackageKey packageKey) {
    return CLASS_REFLECTION_CACHE.getUnchecked(packageKey);
  }

  public CompletableFuture<Void> ensureRegistered(Stream<Schema> schemas) {
    return ensureRegistration(schemas.map(SchemaDescriptor::of));
  }

  private CompletableFuture<Void> ensureRegistration(Stream<SchemaDescriptor> schemas) {
    checkRunning();

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
    schemaRepo.insert(newSchemas)
            .whenComplete((__, e) -> {
              if (e != null) {
                notifyFailed(e);
                for (SchemaDescriptor newSchema : newSchemas) {
                  knownPackersByFingerprint.getUnchecked(newSchema.fingerprint()).completeExceptionally(e);
                }
              }
            });
  }

  public Stream<RecordTypeFamily> getAllRegisteredRecordTypes() {
    checkRunning();
    return typesByFullName.asMap().values().stream();
  }

  public RecordTypeFamily findTypeFamily(Class<? extends SpecificRecordBase> recordClass) {
    return findTypeFamily(recordClass.getName());
  }

  public RecordTypeFamily findTypeFamily(String fullName) {
    checkRunning();
    RecordTypeFamily family = typesByFullName.getIfPresent(fullName);
    checkArgument(family != null, "Unrecognized record-type name", fullName);
    return family;
  }

  /**
   * Gets the {@link RecordPacker} for the provided schema, which is required to have been registered beforehand.
   *
   * @throws IllegalStateException if the provided schema was not previously registered
   * @throws SchemaConflictException if the provided schema conflicted with another registered schema in the {@link SchemaRepo}
   */
  public RecordPacker getPreRegisteredPacker(Schema schema) {
    checkRunning();
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
      throw new RuntimeException(cause);
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
   * @throws SchemaConflictException if the provided schema conflicted with another registered schema in the {@link SchemaRepo}
   */
  public <T extends SpecificRecordBase> SpecificRecordPacker<T> getPreRegisteredPacker(Class<T> recordClass) {
    return getPreRegisteredPacker(specificData(recordClass.getClassLoader()).getSchema(recordClass)).specificPacker(recordClass);
  }


  /**
   * Prepares a {@link AvroCodec.RecordPacker} for the given {@link Schema}, registering the schema with the {@link AvroCodec}
   * if necessary.
   * @param schema
   * @return a {@link CompletableFuture} which holds a {@link AvroCodec.RecordPacker}, which completes only when the
   * {@code fingerprint} has been successfully registered with the {@link SchemaRepo}.
   * @throws SchemaConflictException (via the returned {@link CompletableFuture}) if the provided schema
   * conflicted with another registered schema in the {@link SchemaRepo}
   */
  public CompletableFuture<RecordPacker> getOrRegisterPacker(Schema schema) {
    SchemaDescriptor descriptor = SchemaDescriptor.of(schema);
    return ensureRegistration(Stream.of(descriptor))
            .thenCompose(__ -> knownPackersBySchema.getUnchecked(descriptor));
  }

  /**
   * Finds the registered {@link RecordPacker} for the given {@link SchemaFingerprint}, returning a CompleteableFuture
   * which waits (indefinitely!) for the schema to be published by the the underlying {@link SchemaRepo} if necessary.
   * @return a {@link CompletableFuture} which completes when the fingerprint has been resolved (which should usually be
   * immediate, but may delay indefinitely if the corresponding schema is never published by the SchemaRepo).
   * @throws IllegalStateException if the {@link AvroCodec} is not {@link #isRunning running} (either because it has
   * not been {@link #startAsync started}, has been {@link #stopAsync stopped}, or because it has encountered an
   * unrecoverable error accessing the SchemaRepo)
   */
  public CompletableFuture<RecordPacker> findRegisteredPacker(SchemaFingerprint fingerprint) {
    checkRunning();
    Promise<RecordPacker> packerFuture = knownPackersByFingerprint.getUnchecked(fingerprint);
    if (!packerFuture.isDone()) LOG.warn("Awaiting arrival of unrecognized schema-fingerprint: {}", fingerprint.hexValue());
    return packerFuture;
  }

  /**
   * Finds the registered {@link RecordPacker} for the given {@link SchemaFingerprint}, syncing with the underlying
   * {@link SchemaRepo} if necessary.
   * @return a {@link CompletableFuture} which completes when the fingerprint has been resolved (which should usually be
   * immediate).
   * <p/>
   * Note that this method requires the requested fingerprint to be <em>immediately</em> present in the SchemaRepo.
   * If the SchemaRepo is being asynchronously replicated from an upstream source in a manner that could be reordered
   * vs. messages being consumed via this codec, this method may fail. In such scenarios, {@link #findRegisteredPacker}
   * may be a better choice.
   * @throws IllegalStateException if the {@link AvroCodec} is not {@link #isRunning running} (either because it has
   * not been {@link #startAsync started} or because it has encountered an unrecoverable error accessing the SchemaRepo)
   * @throws IllegalStateException via the returned {@link CompletableFuture} if the {@code fingerprint} could not be
   * resolved with the refreshed contents of the SchemaRepo.
   */
  public CompletableFuture<RecordPacker> findPreRegisteredPacker(SchemaFingerprint fingerprint) {
    checkRunning();
    return Optional.<CompletableFuture<RecordPacker>>ofNullable(knownPackersByFingerprint.getIfPresent(fingerprint))
            .orElseGet(() -> schemaRepo.refresh()
                    .thenCompose(__ -> {
                      Promise<RecordPacker> found = knownPackersByFingerprint.getIfPresent(fingerprint);
                      checkState(found != null, "Unrecognized schema", fingerprint);
                      return found;
                    })
            );
  }

  public CompletableFuture<List<SchemaDescriptor>> getAllRegisteredSchemas(boolean refresh) {
    checkRunning();
    CompletableFuture<?> refreshed = refresh ? schemaRepo.refresh() : CompletableFutures.nullFuture();

    return refreshed.thenApply(ignored -> typesByFullName.asMap().values().stream()
            .flatMap(RecordTypeFamily::getAllVersions)
            .collect(Collectors.toUnmodifiableList())
    );
  }

  private void onSchemaAdded(SchemaDescriptor descriptor) {
    Promise<RecordPacker> promise = knownPackersByFingerprint.getUnchecked(descriptor.fingerprint());
    if (promise.isDone()) return; // we tolerate multiple copies of the same schema

    RegistrationResult registrationResult = typesByFullName.getUnchecked(descriptor.fullName()).addVersion(descriptor);
    if (registrationResult.succeeded()) {
      LOG.info("Loaded schema from repository: {}", descriptor);
      promise.complete(registrationResult.getRegisteredPacker());
    } else {
      SchemaConflictException ex = registrationResult.getConflictException();
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
    RecordTypeFamily family = typesByFullName.getUnchecked(descriptor.fullName());
    Optional<SchemaConflictException> exception = family.checkCompatibility(descriptor);
    return exception.<CompletableFuture<RecordPacker>>map(CompletableFutures::failedFuture)
            .orElseGet(() -> {
              Promise<RecordPacker> promise = knownPackersByFingerprint.getUnchecked(descriptor.fingerprint());
              if (!promise.isDone()) {
                pendingRegistrations.offer(descriptor);
                return CompletableFutures.recoverCompose(promise, SchemaConflictException.class, conflictException ->
                        CompletableFutures.sequence(schemaRepo.delete(descriptor).handle((__, e2) -> {
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


  @Override
  protected void doStart() {
    schemaRepo.startUp(new SchemaRepo.SchemaListener() {
      @Override
      public void onSchemaAdded(SchemaDescriptor schema) {
        AvroCodec.this.onSchemaAdded(schema);
      }

      @Override
      public void onSchemaRemoved(SchemaFingerprint fingerprint) {
        AvroCodec.this.onSchemaRemoved(fingerprint);
      }
    }).thenCompose(__ -> schemaRepo.refresh())
            .whenComplete((__, e) -> {
              if (e == null) {
                notifyStarted();
              } else {
                notifyFailed(e);
              }
            });
  }

  @Override
  protected void doStop() {
    schemaRepo.shutDown().whenComplete((__, e) -> {
//      IllegalStateException stragglerException = new IllegalStateException("AvroCodec was shut down");
      knownPackersByFingerprint.asMap().forEach((fingerprint, promise) -> {
        if (!promise.isDone()) promise.completeExceptionally(new IllegalStateException("AvroCodec was shut down while awaiting schema: " + fingerprint.hexValue()));
      });
      if (e == null) {
        notifyStopped();
      } else {
        notifyFailed(e);
      }
    });
  }

  static BinaryDecoder binaryDecoder(InputStream in) {
    return DecoderFactory.get().binaryDecoder(in, null);
  }

  private void checkRunning() {
    checkState(isRunning(), "AvroCodec wasn't running", this);
  }

  public CompletableFuture<Void> ensureReplicatedFrom(AvroCodec other) {
    checkArgument(this != other, "Tried to replicate identical repo");
    checkRunning();
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
   * Instances of this class are obtained from {@link AvroCodec#getPreRegisteredPacker} or
   * {@link AvroCodec#getOrRegisterPacker}, and are thread-safe and reusable. For example, do something like this
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
      if (record instanceof SpecificRecordBase) {
        return PackableRecord.of((SpecificRecordBase) record, specificPacker(record.getClass()
                .asSubclass(SpecificRecordBase.class)));
      }
      return RecordPackerApi.super.makePackable(record);
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

    public <T extends SpecificRecordBase> SpecificRecordPacker<T> specificPacker(Class<T> recordClass) {
      return new SpecificRecordPacker<>(this, recordClass);
    }

    @SuppressWarnings("unchecked")
    public SpecificRecordPacker<?> inferSpecificPacker(ClassLoader classLoader) {
      return specificPacker(specificData(classLoader).getClass(schema));
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

  /**
   * Represents a lineage of schemas for a type of record, as identified by {@link Schema#getFullName}:
   * when a schema has been updated with multiple versions, all known historical versions will share the
   * same RecordTypeFamily.
   */
  public static class RecordTypeFamily {
    private final String fullName;
    private final Map<SchemaFingerprint, SchemaDescriptor> versionsByFingerprint = new ConcurrentHashMap<>();
    private final List<SchemaDescriptor> orderedVersions = new CopyOnWriteArrayList<>();

    private RecordTypeFamily(String fullName) {
      this.fullName = fullName;
    }

    public String getFullName() {
      return fullName;
    }

    public boolean isInstance(UnpackableRecord record) {
      return versionsByFingerprint.containsKey(record.fingerprint());
    }

    public List<SchemaFingerprint> getAllFingerprints() {
      return orderedVersions.stream()
              .map(SchemaDescriptor::fingerprint)
              .collect(Collectors.toList());
    }

    public Stream<SchemaDescriptor> getAllVersions() {
      return orderedVersions.stream();
    }

    /**
     * Constructs a new {@link SpecificRecordConverter} for the provided {@code recordClass}.
     *
     * @see SpecificRecordConverter
     */
    public <T extends SpecificRecordBase> SpecificRecordConverter<T> newConverter(Class<T> recordClass) {
      return new SpecificRecordConverter<>(this, new SpecificRecordUnpacker<>(recordClass));
    }

    public Optional<SchemaDescriptor> getLatestSchema() {
      return orderedVersions.isEmpty() ? Optional.empty() : Optional.of(orderedVersions.get(orderedVersions.size() - 1));
    }

    public boolean isCompatibleReader(Schema readerSchema) {
      return allSchemas().anyMatch(readerSchema::equals)
              || allSchemas().allMatch(writer -> isCompatibleReaderWriter(readerSchema, writer));
    }

    private Stream<Schema> allSchemas() {
      return orderedVersions.stream().map(SchemaDescriptor::schema);
    }

    public SchemaDescriptor requireLatestSchema() {
      return getLatestSchema().orElseThrow(() -> new IllegalStateException("No writer-schemas registered for type: " + fullName));
    }

    public Optional<SchemaConflictException> checkCompatibility(SchemaDescriptor candidate) {
      Schema schema = candidate.schema();
      List<SchemaConflict> incompatibilities = PairStream.withMappedValues(
                      orderedVersions.stream(),
                      version -> checkBidirectionalCompatibility(schema, version.schema())
              )
              .filterValues(result -> result.getCompatibility() != SchemaCompatibility.SchemaCompatibilityType.COMPATIBLE)
              .map(SchemaConflict::of)
              .collect(Collectors.toUnmodifiableList());
      return Optionals.onlyIfFrom(
              !incompatibilities.isEmpty(),
              () -> new SchemaConflictException(candidate, incompatibilities));
    }

    synchronized RegistrationResult addVersion(SchemaDescriptor schema) {
      return checkCompatibility(schema)
              .map(RegistrationResult::failed)
              .orElseGet(() -> {
                SchemaDescriptor prev = versionsByFingerprint.put(schema.fingerprint(), schema);
                if (prev != null) {
                  if (!prev.equals(schema)) orderedVersions.set(orderedVersions.indexOf(prev), schema);
                } else {
                  orderedVersions.add(schema);
                }
                return new RegistrationResult(new RecordPacker(schema, this), null);
              });
    }

    private static boolean isCompatibleReaderWriter(Schema readerSchema, Schema writerSchema) {
      return SchemaCompatibility.checkReaderWriterCompatibility(readerSchema, writerSchema).getType() == SchemaCompatibility.SchemaCompatibilityType.COMPATIBLE;
    }

    @Override
    public boolean equals(Object o) {
      return this == o;
    }

    @Override
    public int hashCode() {
      return getFullName().hashCode();
    }
  }

  private static SchemaCompatibility.SchemaCompatibilityResult checkBidirectionalCompatibility(
          Schema a, Schema b
  ) {
    return SchemaCompatibility.checkReaderWriterCompatibility(a, b).getResult()
            .mergedWith(SchemaCompatibility.checkReaderWriterCompatibility(b, a).getResult());
  }

  public static class SchemaConflictException extends RuntimeException {
    public SchemaConflictException(SchemaDescriptor conflict, List<SchemaConflict> conflicts) {
      super(String.format("Incompatible schema:\n  %s\n  conflicts:  %s", conflict, conflicts));
    }
  }

  @Value.Immutable
  @Tuple
  public interface SchemaConflict {
    static SchemaConflict of(SchemaDescriptor conflictingSchema, SchemaCompatibility.SchemaCompatibilityResult result) {
      return ImmutableSchemaConflict.of(conflictingSchema, result);
    }

    SchemaDescriptor conflictingSchema();
    SchemaCompatibility.SchemaCompatibilityResult compatibilityResult();
  }

  private static class RegistrationResult {
    private final RecordPacker registeredPacker;
    private final SchemaConflictException conflictException;

    private RegistrationResult(RecordPacker registeredPacker, SchemaConflictException conflictException) {
      assert registeredPacker == null ^ conflictException == null; // exactly one of these must be null
      this.registeredPacker = registeredPacker;
      this.conflictException = conflictException;
    }

    boolean succeeded() {
      return registeredPacker != null;
    }

    static RegistrationResult failed(SchemaConflictException conflictException) {
      return new RegistrationResult(null, conflictException);
    }

    RecordPacker getRegisteredPacker() {
      return registeredPacker;
    }

    SchemaConflictException getConflictException() {
      return conflictException;
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
    default List<Schema> findPublishedSchemas() {
      Set<Class<? extends SpecificRecordBase>> recordClasses = findRecordClasses(this);

      SpecificData specificData = specificData(classLoader());

      return recordClasses.stream()
              .map(specificData::getSchema)
              .filter(AvroCodec::isMarkedForPublication)
              .collect(ImmutableList.toImmutableList());
    }
  }

  @Identifier
  abstract static class SchemaDescriptorImpl implements SchemaDescriptor {

    @Override
    @Value.Auxiliary @Value.Derived
    public SchemaFingerprint fingerprint() {
      return SchemaFingerprint.of(schema());
    }

    @Override
    public String toString() {
      return String.format("Schema[%s]%s", fingerprint().hexValue(), schema());
    }
  }
}
