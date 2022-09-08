package io.upstartproject.avrocodec;

import com.google.common.collect.ClassToInstanceMap;
import org.apache.avro.Schema;
import org.apache.avro.io.DatumReader;
import org.apache.avro.specific.SpecificData;
import org.apache.avro.specific.SpecificDatumReader;
import org.apache.avro.specific.SpecificRecordBase;
import org.immutables.value.Value;
import upstart.util.annotations.Tuple;
import upstart.util.collect.Optionals;

import java.util.Optional;

@Tuple
public interface SpecificRecordType<R extends SpecificRecordBase> {
  ClassValue<SpecificRecordType<?>> CACHE = new ClassValue<>() {
    @Override
    protected SpecificRecordType<?> computeValue(Class<?> type) {
      return ImmutableSpecificRecordType.of(type.asSubclass(SpecificRecordBase.class));
    }
  };

  @SuppressWarnings("unchecked")
  static <T extends SpecificRecordBase> SpecificRecordType<T> of(Class<T> recordClass) {
    return (SpecificRecordType<T>) CACHE.get(recordClass);

  }

  Class<R> recordClass();

  @Value.Lazy
  default Optional<SchemaDescriptor> optionalPublishedSchemaDescriptor() {
    return Optionals.onlyIfFrom(AvroPublisher.isMarkedForPublication(schema()), () -> SchemaDescriptor.of(schema()));
  }

  default SchemaDescriptor publishedSchemaDescriptor() {
    return optionalPublishedSchemaDescriptor().orElseThrow(() -> new IllegalStateException("Schema " + recordClass().getName() + " is not marked for publication"));
  }

  @Value.Derived
  @Value.Auxiliary
  default Schema schema() {
    return specificData().getSchema(recordClass());
  }

  @Value.Derived
  @Value.Auxiliary
  default SpecificData specificData() {
    return SpecificData.getForClass(recordClass());
  }

  @SuppressWarnings("unchecked")
  default DatumReader<R> createDatumReader() {
    return specificData().createDatumReader(schema());
  }
}
