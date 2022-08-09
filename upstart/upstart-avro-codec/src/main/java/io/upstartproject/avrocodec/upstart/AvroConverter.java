package io.upstartproject.avrocodec.upstart;

import com.google.inject.TypeLiteral;
import io.upstartproject.avrocodec.AvroDecoder;
import io.upstartproject.avrocodec.AvroTaxonomy;
import io.upstartproject.avrocodec.RecordTypeFamily;
import upstart.guice.TypeLiterals;
import io.upstartproject.avrocodec.RecordConverterApi;
import io.upstartproject.avrocodec.SpecificRecordConverter;
import io.upstartproject.avrocodec.UnpackableRecord;
import org.apache.avro.specific.SpecificRecordBase;
import upstart.managedservices.ServiceTransformer;

import javax.inject.Inject;
import java.util.concurrent.CompletableFuture;

public class AvroConverter<T extends SpecificRecordBase>
        implements RecordConverterApi<T>
{

  private final SpecificRecordConverter<T> converter;
  private final AvroDecoder decoder;

  @Inject
  AvroConverter(TypeLiteral<T> recordTypeLiteral, AvroDecoder decoder) {
    this.decoder = decoder;
    converter = decoder.recordConverter(TypeLiterals.getRawType(recordTypeLiteral));
  }

  public CompletableFuture<T> readPackedRecord(byte[] bytes) {
    return readPackedRecord(bytes, decoder);
  }

  @Override
  public T convert(UnpackableRecord record) {
    return converter.convert(record);
  }

  @Override
  public RecordTypeFamily writerTypeFamily() {
    return converter.writerTypeFamily();
  }
}
