package io.upstartproject.avrocodec.upstart;

import io.upstartproject.avro.MessageEnvelope;
import io.upstartproject.avrocodec.MessageMetadata;
import upstart.config.UpstartModule;

public class AvroEnvelopeModule extends UpstartModule {
  private final DataStore dataStore;

  public AvroEnvelopeModule(DataStore dataStore) {
    super(dataStore);
    this.dataStore = dataStore;
  }

  @Override
  protected void configure() {
    AvroPublicationModule.bindAvroFromRecordPackage(binder(), dataStore, MessageEnvelope.class);
  }
}
