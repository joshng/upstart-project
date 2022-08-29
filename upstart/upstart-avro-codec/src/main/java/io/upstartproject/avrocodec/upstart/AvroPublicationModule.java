package io.upstartproject.avrocodec.upstart;

import com.google.common.collect.Streams;
import com.google.inject.Binder;
import com.google.inject.Key;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.util.Types;
import io.upstartproject.avrocodec.AvroPublisher;
import io.upstartproject.avrocodec.AvroTaxonomy;
import io.upstartproject.avrocodec.EnvelopeCodec;
import io.upstartproject.avrocodec.MemorySchemaRegistry;
import io.upstartproject.avrocodec.SchemaRegistry;
import io.upstartproject.avrocodec.SpecificRecordType;
import org.apache.avro.specific.SpecificRecordBase;
import upstart.config.UpstartModule;
import upstart.guice.AnnotationKeyedPrivateModule;
import upstart.guice.PrivateBinding;
import upstart.guice.TypeLiterals;
import upstart.managedservices.ServiceLifecycle;
import upstart.util.concurrent.services.NotifyingService;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Set;

/**
 * Arranges for {@link AvroPublisher} and {@link EnvelopeCodec} instances to be available for {@link Inject injection}.
 * <p/>
 * Requires a binding for a {@link SchemaRegistry} annotated with the given {@link Annotation} to also be configured
 * elsewhere.
 *
 * @see MemorySchemaRegistry
 */
public class AvroPublicationModule extends UpstartModule {
  public static final Type PACKAGE_KEY_SET_TYPE = Types.setOf(AvroPublisher.PackageKey.class);
  public static final Type RECORD_TYPE_SET_TYPE = Types.setOf(Types.newParameterizedType(SpecificRecordType.class, Types.subtypeOf(SpecificRecordBase.class)));

  private final Annotation annotation;

  public AvroPublicationModule(Annotation annotation) {
    super(annotation);
    this.annotation = annotation;
  }

  public Annotation annotation() {
    return annotation;
  }

  public static Multibinder<AvroPublisher.PackageKey> avroPackageBinder(Binder binder, Annotation annotation) {
    binder.install(new AvroPublicationModule(annotation));
    return Multibinder.newSetBinder(binder, Key.get(AvroPublisher.PackageKey.class, annotation));
  }

  public static Multibinder<SpecificRecordType<?>> avroTypeBinder(Binder binder, Annotation annotation) {
    binder.install(new AvroPublicationModule(annotation));
    //noinspection Convert2Diamond
    return Multibinder.newSetBinder(binder, Key.get(new TypeLiteral<SpecificRecordType<? extends SpecificRecordBase>>() {}, annotation));
  }

  @SafeVarargs
  public static void publishAvroClasses(Binder binder, Annotation annotation, Class<? extends SpecificRecordBase>... recordClasses) {
    Multibinder<SpecificRecordType<?>> multibinder = avroTypeBinder(binder, annotation);
    for (Class<? extends SpecificRecordBase> recordClass : recordClasses) {
      SpecificRecordType<? extends SpecificRecordBase> recordType = SpecificRecordType.of(recordClass);
      recordType.publishedSchemaDescriptor(); // assert the schema is marked for publication
      multibinder.addBinding().toInstance(recordType);
    }
  }

  public static void publishAvroFromPackage(Binder binder, Annotation annotation, String packageName) {
    publishAvroFromPackage(
            binder,
            annotation,
            AvroPublisher.PackageKey.of(packageName, AvroPublicationModule.class.getClassLoader())
    );
  }

  public static void publishAvroFromPackage(Binder binder, Annotation annotation, AvroPublisher.PackageKey packageKey) {
    avroPackageBinder(binder, annotation).addBinding().toInstance(packageKey);
  }

  public static void publishAvroFromRecordPackage(
          Binder binder,
          Annotation annotation,
          Class<? extends SpecificRecordBase> exampleClass
  ) {
    publishAvroFromPackage(binder, annotation, AvroPublisher.packageKey(exampleClass));
  }

  @Override
  protected void configure() {
    avroPackageBinder(binder(), annotation);
    avroTypeBinder(binder(), annotation);
    install(new AvroTaxonomyModule(annotation));
    install(new AnnotationKeyedPrivateModule(
            annotation,
            AvroPublicationService.class,
            AvroPublisher.class,
            EnvelopeCodec.class  // TODO: separate EnvelopeCodec to EnvelopePublisher/EnvelopeDecoder
    ) {
      @Override
      protected void configurePrivateScope() {
        bind(AvroPublisher.class).toProvider(AvroPublicationService.class);
        bindToAnnotatedKey(Key.get(AvroTaxonomy.class));
        bindPrivateBindingToAnnotatedKey(PACKAGE_KEY_SET_TYPE);
        bindPrivateBindingToAnnotatedKey(RECORD_TYPE_SET_TYPE);
      }
    });

    serviceManager().manage(Key.get(AvroPublicationService.class, annotation));
  }

  // TODO: move this concern into the AvroPublisher itself
  @Singleton
  @ServiceLifecycle(ServiceLifecycle.Phase.Infrastructure)
  public static class AvroPublicationService extends NotifyingService implements Provider<AvroPublisher> {
    private final AvroPublisher publisher;
    private final Set<AvroPublisher.PackageKey> packagesToRegister;
    private final Set<SpecificRecordType<? extends SpecificRecordBase>> typesToRegister;
    private final Annotation annotation;

    @Inject
    AvroPublicationService(
            @PrivateBinding Annotation annotation,
            @PrivateBinding Set<AvroPublisher.PackageKey> packagesToRegister,
            @PrivateBinding Set<SpecificRecordType<? extends SpecificRecordBase>> typesToRegister,
            AvroTaxonomy taxonomy
    ) {
      this.publisher = new AvroPublisher(taxonomy);
      this.packagesToRegister = packagesToRegister;
      this.annotation = annotation;
      this.typesToRegister = typesToRegister;
    }

    @Override
    protected void doStart() {
      startWith(publisher.ensureRegistered(Streams.concat(
              packagesToRegister.stream()
                      .map(AvroPublisher.PackageKey::findPublishedTypes)
                      .flatMap(List::stream),
              typesToRegister.stream()
      ).distinct()));
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
      return super.serviceName() + "(" + annotation + ")";
    }
  }

}
