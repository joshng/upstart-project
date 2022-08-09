package io.upstartproject.avrocodec.upstart;

import com.google.inject.Key;
import io.upstartproject.avrocodec.AvroDecoder;
import io.upstartproject.avrocodec.AvroTaxonomy;
import io.upstartproject.avrocodec.SchemaRegistry;
import upstart.config.UpstartModule;
import upstart.managedservices.ServiceLifecycle;

public class AvroTaxonomyModule extends UpstartModule {
  private final DataStore dataStore;

  public AvroTaxonomyModule(DataStore dataStore) {
    super(dataStore);
    this.dataStore = dataStore;
  }

  @Override
  protected void configure() {
    install(new AvroPublicationModule.DataStoreModule(dataStore) {
      @Override
      protected void configure() {
        super.configure();
        bindUnannotatedFromDataStore(SchemaRegistry.class);
      }
    }.exposing(AvroTaxonomy.class, AvroDecoder.class));
    serviceManager().manage(Key.get(AvroTaxonomy.class, dataStore), ServiceLifecycle.Phase.Infrastructure);
  }
}
