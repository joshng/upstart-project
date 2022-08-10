package io.upstartproject.avrocodec.upstart;

import com.google.inject.Binder;
import com.google.inject.Key;
import com.google.inject.PrivateModule;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.Multibinder;
import io.upstartproject.avrocodec.AvroPublisher;
import io.upstartproject.avrocodec.AvroTaxonomy;
import io.upstartproject.avrocodec.MemorySchemaRegistry;
import io.upstartproject.avrocodec.SchemaRegistry;
import upstart.config.UpstartModule;
import upstart.guice.TypeLiterals;
import upstart.util.concurrent.services.NotifyingService;
import upstart.managedservices.ServiceLifecycle;
import upstart.util.concurrent.CompletableFutures;
import io.upstartproject.avrocodec.EnvelopeCodec;
import org.apache.avro.specific.SpecificRecordBase;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * Arranges for {@link AvroPublisher} and {@link EnvelopeCodec} instances to be available for {@link Inject injection}.
 * <p/>
 * Requires a binding for a {@link SchemaRegistry} annotated with the given {@link DataStore} to also be configured
 * elsewhere.
 *
 * @see MemorySchemaRegistry
 */
public class AvroPublicationModule extends UpstartModule {
  public static final TypeLiteral<Set<AvroPublisher.PackageKey>> PACKAGE_KEY_SET = TypeLiterals.<Set<AvroPublisher.PackageKey>>getParameterized(Set.class, AvroPublisher.PackageKey.class);
  private final DataStore dataStore;

  public AvroPublicationModule(DataStore dataStore) {
    super(dataStore);
    this.dataStore = dataStore;
  }

  public static Multibinder<AvroPublisher.PackageKey> avroPackageBinder(Binder binder, DataStore dataStore) {
    binder.install(new AvroPublicationModule(dataStore));
    return Multibinder.newSetBinder(binder, Key.get(AvroPublisher.PackageKey.class, dataStore));
  }

  public static void bindAvroFromPackage(Binder binder, DataStore dataStore, String packageName) {
    bindAvroFromPackage(binder, dataStore, AvroPublisher.PackageKey.of(packageName, AvroPublicationModule.class.getClassLoader()));
  }

  public static void bindAvroFromPackage(Binder binder, DataStore dataStore, AvroPublisher.PackageKey packageKey) {
    avroPackageBinder(binder, dataStore).addBinding().toInstance(packageKey);
  }

  public static void bindAvroFromRecordPackage(Binder binder, DataStore dataStore, Class<? extends SpecificRecordBase> exampleClass) {
    bindAvroFromPackage(binder, dataStore, AvroPublisher.packageKey(exampleClass));
  }

  @Override
  protected void configure() {
    avroPackageBinder(binder(), dataStore);
    install(new AvroTaxonomyModule(dataStore));
    Key<AvroPublicationService> serviceKey = Key.get(AvroPublicationService.class, dataStore);
    install(new DataStoreModule(dataStore) {
      @Override
      protected void configure() {
        super.configure();
        bindUnannotatedFromDataStore(PACKAGE_KEY_SET);
        bindUnannotatedFromDataStore(AvroTaxonomy.class);
        bind(AvroPublisher.class).toProvider(AvroPublicationService.class);
      }
      // TODO: separate EnvelopeCodec to EnvelopePublisher/EnvelopeDecoder
    }.exposing(AvroPublicationService.class, AvroPublisher.class, EnvelopeCodec.class));
    serviceManager().manage(serviceKey);
  }

  // TODO: move this concern into the AvroPublisher itself
  @Singleton
  @ServiceLifecycle(ServiceLifecycle.Phase.Infrastructure)
  public static class AvroPublicationService extends NotifyingService implements Provider<AvroPublisher> {
    private final AvroPublisher publisher;
    private final Set<AvroPublisher.PackageKey> packagesToRegister;
    private final DataStore dataStore;

    @Inject
    AvroPublicationService(
            DataStore dataStore,
            Set<AvroPublisher.PackageKey> packagesToRegister,
            AvroTaxonomy taxonomy
    ) {
      this.publisher = new AvroPublisher(taxonomy);
      this.packagesToRegister = packagesToRegister;
      this.dataStore = dataStore;
    }

    @Override
    protected void doStart() {
      startWith(CompletableFutures.allOf(packagesToRegister.stream().map(publisher::registerSpecificRecordSchemas)));
    }

    public <T extends SpecificRecordBase> AvroPacker<T> packerFor(TypeLiteral<T> recordClass) {
      return packerFor(TypeLiterals.getRawType(recordClass));
    }

    public <T extends SpecificRecordBase> AvroPacker<T> packerFor(Class<T> recordClass) {
      return new AvroPacker<>(recordClass, this);
    }

    @Override
    protected void doStop() {
      notifyStopped();
    }

    AvroPublisher getPublisher() {
      return publisher;
    }

    @Override
    public AvroPublisher get() {
      return publisher;
    }

    @Override
    public String serviceName() {
      return super.serviceName() + "(" + dataStore.value() + ")";
    }
  }

  public static class DataStoreModule extends PrivateModule {
    private final DataStore dataStore;
    private final List<TypeLiteral<?>> exposedTypes = new ArrayList<>();

    public DataStoreModule(DataStore dataStore) {
      this.dataStore = dataStore;
    }

    protected  <T> Key<T> bindDataStoreToUnannotated(Class<T> boundType) {
      return bindDataStoreToUnannotated(TypeLiteral.get(boundType));
    }

    protected  <T> Key<T> bindParameterizedDataStoreToPrivate(Type boundType, Type... typeArguments) {
      return bindDataStoreToUnannotated(TypeLiterals.getParameterized(boundType, typeArguments));
    }

    protected  <T> Key<T> bindDataStoreToUnannotated(TypeLiteral<T> boundType) {
      Key<T> boundKey = keyedByDataStore(boundType);
      bind(boundKey).to(boundType);
      return boundKey;
    }

    protected  <T> Key<T> bindUnannotatedFromDataStore(Class<T> boundType) {
      return bindUnannotatedFromDataStore(TypeLiteral.get(boundType));
    }

    protected  <T> Key<T> bindUnannotatedFromDataStore(TypeLiteral<T> boundType) {
      Key<T> annotatedKey = keyedByDataStore(boundType);
      bind(boundType).to(annotatedKey);
      return annotatedKey;
    }

    protected <T> Key<T> keyedByDataStore(TypeLiteral<T> boundType) {
      return Key.get(boundType, dataStore);
    }

    public DataStoreModule exposing(Type... boundTypes) {
      for (Type boundType : boundTypes) {
        exposedTypes.add(TypeLiteral.get(boundType));
      }
      return this;
    }

    public DataStoreModule exposing(TypeLiteral<?>... boundTypes) {
      exposedTypes.addAll(Arrays.asList(boundTypes));
      return this;
    }

    @Override
    protected void configure() {
      bind(DataStore.class).toInstance(dataStore);
      for (TypeLiteral<?> exposedType : exposedTypes) {
        expose(bindDataStoreToUnannotated(exposedType));
      }
    }
  }
}
