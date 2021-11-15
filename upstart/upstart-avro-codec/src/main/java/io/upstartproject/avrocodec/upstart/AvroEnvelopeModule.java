package io.upstartproject.avrocodec.upstart;

import io.upstartproject.avro.MessageEnvelope;
import io.upstartproject.avrocodec.MessageMetadata;
import upstart.config.annotations.ConfigPath;
import upstart.config.UpstartModule;

public class AvroEnvelopeModule extends UpstartModule {
  @Override
  protected void configure() {
    install(AvroModule.class);
    AvroEnvelopeConfig config = bindConfig(AvroEnvelopeConfig.class);
    bind(MessageMetadata.class).toInstance(config.metadata());
    AvroModule.bindAvroFromRecordPackage(binder(), MessageEnvelope.class);
  }

  @ConfigPath("upstart.messageEnvelope")
  public interface AvroEnvelopeConfig{
    MessageMetadata metadata();

  }
}
