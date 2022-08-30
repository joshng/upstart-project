package io.upstartproject.avrocodec;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import org.apache.avro.Schema;
import org.apache.avro.SchemaCompatibility;
import org.apache.avro.specific.SpecificRecordBase;
import org.immutables.value.Value;
import upstart.util.annotations.Tuple;
import upstart.util.collect.Optionals;
import upstart.util.collect.PairStream;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Represents a lineage of schemas for a type of record, as identified by {@link Schema#getFullName}:
 * when a schema has been updated with multiple versions, all known historical versions will share the
 * same RecordTypeFamily.
 */
@JsonSerialize(using = ToStringSerializer.class)
public class RecordTypeFamily {
  private final String fullName;
  private final Map<SchemaFingerprint, SchemaDescriptor> versionsByFingerprint = new ConcurrentHashMap<>();
  private final List<SchemaDescriptor> orderedVersions = new CopyOnWriteArrayList<>();

  RecordTypeFamily(String fullName) {
    this.fullName = fullName;
  }

  public static SchemaCompatibility.SchemaCompatibilityResult checkBidirectionalCompatibility(
          Schema a, Schema b
  ) {
    return SchemaCompatibility.checkReaderWriterCompatibility(a, b).getResult()
            .mergedWith(SchemaCompatibility.checkReaderWriterCompatibility(b, a).getResult());
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

  public Optional<AvroSchemaConflictException> checkCompatibility(SchemaDescriptor candidate) {
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
            () -> new AvroSchemaConflictException(candidate, incompatibilities)
    );
  }

  synchronized RegistrationResult addVersion(SchemaDescriptor schema) {
    return checkCompatibility(schema)
            .map(this::failedRegistration)
            .orElseGet(() -> {
              SchemaDescriptor prev = versionsByFingerprint.put(schema.fingerprint(), schema);
              if (prev != null) {
                if (!prev.equals(schema)) orderedVersions.set(orderedVersions.indexOf(prev), schema);
              } else {
                orderedVersions.add(schema);
              }
              return new RegistrationResult(schema, null);
            });
  }


  RegistrationResult failedRegistration(AvroSchemaConflictException conflictException) {
    return new RegistrationResult(null, conflictException);
  }

  private static boolean isCompatibleReaderWriter(Schema readerSchema, Schema writerSchema) {
    return SchemaCompatibility.checkReaderWriterCompatibility(
            readerSchema,
            writerSchema
    ).getType() == SchemaCompatibility.SchemaCompatibilityType.COMPATIBLE;
  }

  @Override
  public boolean equals(Object o) {
    return this == o;
  }

  @Override
  public int hashCode() {
    return getFullName().hashCode();
  }

  @Override
  public String toString() {
    return fullName;
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

  public class RegistrationResult {
    private final SchemaDescriptor registeredSchema;
    private final AvroSchemaConflictException conflictException;

    private RegistrationResult(SchemaDescriptor registeredSchema, AvroSchemaConflictException conflictException) {
      assert registeredSchema == null ^ conflictException == null; // exactly one of these must be null
      this.registeredSchema = registeredSchema;
      this.conflictException = conflictException;
    }

    boolean succeeded() {
      return registeredSchema != null;
    }

    SchemaDescriptor registeredSchema() {
      if (conflictException != null) throw conflictException;
      return registeredSchema;
    }

    AvroSchemaConflictException getConflictException() {
      return conflictException;
    }

    public RecordTypeFamily typeFamily() {
      return RecordTypeFamily.this;
    }
  }
}
