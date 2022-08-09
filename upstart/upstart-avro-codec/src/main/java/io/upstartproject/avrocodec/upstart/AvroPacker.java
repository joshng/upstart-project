package io.upstartproject.avrocodec.upstart;

import com.google.inject.TypeLiteral;
import io.upstartproject.avro.PackedRecord;
import io.upstartproject.avrocodec.RecordTypeFamily;
import upstart.guice.TypeLiterals;
import io.upstartproject.avrocodec.RecordPackerApi;
import io.upstartproject.avrocodec.SchemaFingerprint;
import io.upstartproject.avrocodec.SpecificRecordPacker;
import org.apache.avro.Schema;
import org.apache.avro.specific.SpecificRecordBase;
import upstart.managedservices.ServiceTransformer;

import javax.inject.Inject;

public class AvroPacker<T extends SpecificRecordBase>
        extends ServiceTransformer<AvroPublicationModule.AvroPublicationService, SpecificRecordPacker<T>>
        implements RecordPackerApi<T>
{
  private final Class<T> recordType;

  @Inject
  public AvroPacker(TypeLiteral<T> recordTypeLiteral, AvroPublicationModule.AvroPublicationService avroPublicationService) {
    this(TypeLiterals.getRawType(recordTypeLiteral), avroPublicationService);
  }

  public AvroPacker(Class<T> type, AvroPublicationModule.AvroPublicationService avroPublicationService) {
    super(avroPublicationService, () -> avroPublicationService.getPublisher().getPreRegisteredPacker(type));
    recordType = type;
  }

  @Override
  public PackedRecord pack(T record) {
    return get().pack(record);
  }

  @Override
  public RecordTypeFamily getTypeFamily() {
    return get().getTypeFamily();
  }

  @Override
  public SchemaFingerprint fingerprint() {
    return get().fingerprint();
  }

  @Override
  public Schema schema() {
    return get().schema();
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + "<" + recordType.getName() + ">";
  }
}
