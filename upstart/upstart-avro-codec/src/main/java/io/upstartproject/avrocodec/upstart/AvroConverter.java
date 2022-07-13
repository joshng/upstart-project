package io.upstartproject.avrocodec.upstart;

import com.google.inject.TypeLiteral;
import upstart.guice.TypeLiterals;
import io.upstartproject.avrocodec.AvroCodec;
import io.upstartproject.avrocodec.RecordConverterApi;
import io.upstartproject.avrocodec.SpecificRecordConverter;
import io.upstartproject.avrocodec.UnpackableRecord;
import org.apache.avro.specific.SpecificRecordBase;
import upstart.managedservices.ServiceTransformer;

import javax.inject.Inject;
import java.util.concurrent.CompletableFuture;

public class AvroConverter<T extends SpecificRecordBase>
        extends ServiceTransformer<AvroModule.AvroCodecService, SpecificRecordConverter<T>>
        implements RecordConverterApi<T>
{

  @Inject
  AvroConverter(TypeLiteral<T> recordTypeLiteral, AvroModule.AvroCodecService avroCodecService) {
    super(avroCodecService, () -> avroCodecService.getCodec()
            .recordConverter(TypeLiterals.getRawType(recordTypeLiteral)));
  }

  public CompletableFuture<T> readPackedRecord(byte[] bytes) {
    return readPackedRecord(bytes, service().getCodec());
  }

  @Override
  public T convert(UnpackableRecord record) {
    return get().convert(record);
  }

  @Override
  public AvroCodec.RecordTypeFamily writerTypeFamily() {
    return get().writerTypeFamily();
  }
}
