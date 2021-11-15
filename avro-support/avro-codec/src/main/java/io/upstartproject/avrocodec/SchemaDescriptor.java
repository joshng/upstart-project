package io.upstartproject.avrocodec;

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
}
