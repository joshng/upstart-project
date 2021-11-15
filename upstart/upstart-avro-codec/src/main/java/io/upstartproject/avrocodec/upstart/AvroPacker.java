package io.upstartproject.avrocodec.upstart;

import com.google.inject.TypeLiteral;
import io.upstartproject.avro.PackedRecord;
import upstart.guice.TypeLiterals;
import io.upstartproject.avrocodec.AvroCodec;
import io.upstartproject.avrocodec.RecordPackerApi;
import io.upstartproject.avrocodec.SchemaFingerprint;
import io.upstartproject.avrocodec.SpecificRecordPacker;
import org.apache.avro.Schema;
import org.apache.avro.specific.SpecificRecordBase;

import javax.inject.Inject;

public class AvroPacker<T extends SpecificRecordBase>
        extends ServiceTransformer<SpecificRecordPacker<T>>
        implements RecordPackerApi<T>
{

  private final Class<T> recordType;

  @Inject
  public AvroPacker(TypeLiteral<T> recordTypeLiteral, AvroModule.AvroCodecService avroCodecService) {
    super(avroCodecService, () -> avroCodecService.getCodec()
            .getPreRegisteredPacker(TypeLiterals.getRawType(recordTypeLiteral)));
    recordType = TypeLiterals.getRawType(recordTypeLiteral);
  }

  @Override
  public PackedRecord pack(T record) {
    return get().pack(record);
  }

  @Override
  public AvroCodec.RecordTypeFamily getTypeFamily() {
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
