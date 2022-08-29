package io.upstartproject.avrocodec.upstart;

import io.upstartproject.avro.MessageEnvelope;
import upstart.config.UpstartModule;

import java.lang.annotation.Annotation;

public class AvroEnvelopeModule extends UpstartModule {
  private final Annotation annotation;

  public AvroEnvelopeModule(Annotation annotation) {
    super(annotation);
    this.annotation = annotation;
  }

  @Override
  protected void configure() {
    AvroPublicationModule.publishAvroFromRecordPackage(binder(), annotation, MessageEnvelope.class);
  }
}
