package io.upstartproject.avrocodec;

import org.immutables.value.Value;
import upstart.util.annotations.Identifier;
import upstart.util.exceptions.UncheckedIO;
import org.apache.avro.Schema;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

public interface SchemaDescriptor {
  static SchemaDescriptor of(Schema schema) {
    return ImmutableSchemaDescriptorImpl.of(schema);
  }

  static SchemaDescriptor of(String schemaJson) {
    return of(new Schema.Parser().parse(schemaJson));
  }

  static SchemaDescriptor of(byte[] schemaJsonBytes) {
    return from(new ByteArrayInputStream(schemaJsonBytes));
  }

  static SchemaDescriptor from(InputStream schemaJsonStream) {
    return UncheckedIO.getUnchecked(() -> of(new Schema.Parser().parse(schemaJsonStream)));
  }

  Schema schema();

  SchemaFingerprint fingerprint();

  default String fullName() {
    return schema().getFullName();
  }

  @Identifier
  abstract class SchemaDescriptorImpl implements SchemaDescriptor {

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
