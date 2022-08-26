package io.upstartproject.avrocodec.upstart;

import com.google.inject.Binder;
import com.google.inject.Key;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.Multibinder;
import io.upstartproject.avrocodec.AvroPublisher;
import io.upstartproject.avrocodec.AvroTaxonomy;
import io.upstartproject.avrocodec.MemorySchemaRegistry;
import io.upstartproject.avrocodec.SchemaRegistry;
import upstart.config.UpstartModule;
import upstart.guice.AnnotationKeyedPrivateModule;
import upstart.guice.PrivateBinding;
import upstart.guice.TypeLiterals;
import upstart.util.concurrent.services.NotifyingService;
import upstart.managedservices.ServiceLifecycle;
import upstart.util.concurrent.CompletableFutures;
import io.upstartproject.avrocodec.EnvelopeCodec;
import org.apache.avro.specific.SpecificRecordBase;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import java.lang.annotation.Annotation;
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
  public static final TypeLiteral<Set<AvroPublisher.PackageKey>> PACKAGE_KEY_SET = TypeLiterals.getParameterized(
          Set.class,
          AvroPublisher.PackageKey.class
  );
  private final Annotation annotation;

  public AvroPublicationModule(Annotation annotation) {
    super(annotation);
    this.annotation = annotation;
  }

  public static Multibinder<AvroPublisher.PackageKey> avroPackageBinder(Binder binder, Annotation annotation) {
    binder.install(new AvroPublicationModule(annotation));
    return Multibinder.newSetBinder(binder, Key.get(AvroPublisher.PackageKey.class, annotation));
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
        bindPrivateBindingToAnnotatedKey(PACKAGE_KEY_SET);
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
    private final Annotation annotation;

    @Inject
    AvroPublicationService(
            @PrivateBinding Annotation annotation,
            @PrivateBinding Set<AvroPublisher.PackageKey> packagesToRegister,
            AvroTaxonomy taxonomy
    ) {
      this.publisher = new AvroPublisher(taxonomy);
      this.packagesToRegister = packagesToRegister;
      this.annotation = annotation;
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
      return super.serviceName() + "(" + annotation + ")";
    }
  }

}
