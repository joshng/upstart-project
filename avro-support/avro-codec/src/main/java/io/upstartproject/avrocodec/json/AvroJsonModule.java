package io.upstartproject.avrocodec.json;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.apache.avro.Schema;
import org.apache.avro.specific.SpecificData;
import org.apache.avro.specific.SpecificRecordBase;
import org.kohsuke.MetaInfServices;

@MetaInfServices(com.fasterxml.jackson.databind.Module.class)
public class AvroJsonModule extends SimpleModule {
  public AvroJsonModule() {
    super("AvroJsonModule");
    setMixInAnnotation(SpecificRecordBase.class, SpecificRecordJsonMixin.class);
  }

  public interface SpecificRecordJsonMixin {
    @JsonIgnore
    Schema getSchema();
    @JsonIgnore
    SpecificData getSpecificData();
  }
}
