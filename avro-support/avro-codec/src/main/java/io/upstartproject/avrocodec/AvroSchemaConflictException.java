package io.upstartproject.avrocodec;

import java.util.List;

public class AvroSchemaConflictException extends RuntimeException {
  public AvroSchemaConflictException(SchemaDescriptor conflict, List<RecordTypeFamily.SchemaConflict> conflicts) {
    super(String.format("Incompatible schema:\n  %s\n  conflicts:  %s", conflict, conflicts));
  }
}
