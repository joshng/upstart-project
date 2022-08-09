package io.upstartproject.avrocodec;

import com.fasterxml.jackson.annotation.JsonValue;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import io.upstartproject.avro.PackedRecord;
import org.apache.avro.Schema;
import org.immutables.value.Value;

/**
 * A type-safe wrapper around a {@link long} value representing the aspects of an avro {@link Schema} which are
 * relevant for reading and determining cross-schema conversions: if two schemas share an identical {@link SchemaFingerprint},
 * then they are effectively interchangeable for reading, writing, and conversion.
 * <p/>
 * The computation of a schema's fingerprint is performed by {@link SchemaNormalization2#resolvingFingerprint64}.
 * @see SchemaNormalization2#resolvingFingerprint64
 * @see PackedRecord#getFingerprint
 * @see AvroPublisher#findRegisteredPacker
 */
@Value.Immutable(builder = false, intern = true)
@Value.Style(allParameters = true)
public interface SchemaFingerprint {
  LoadingCache<Schema, SchemaFingerprint> CACHE = CacheBuilder.newBuilder()
          .build(new CacheLoader<Schema, SchemaFingerprint>() {
            @Override
            public SchemaFingerprint load(Schema key) {
              return of(SchemaNormalization2.resolvingFingerprint64(key));
            }
          });

  static SchemaFingerprint of(Schema schema) {
    return CACHE.getUnchecked(schema);
  }

  static SchemaFingerprint of(long fingerprint) {
    return ImmutableSchemaFingerprint.of(fingerprint);
  }

  long value();

  @Value.Derived
  @Value.Auxiliary
  @JsonValue
  default String hexValue() {
    return String.format("0x%016x", value());
  }
}
