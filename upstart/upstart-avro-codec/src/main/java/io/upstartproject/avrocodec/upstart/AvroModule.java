package io.upstartproject.avrocodec.upstart;

import com.google.inject.Binder;
import com.google.inject.Provides;
import com.google.inject.multibindings.Multibinder;
import upstart.config.UpstartModule;
import upstart.util.concurrent.services.NotifyingService;
import upstart.util.concurrent.services.ComposableService;
import upstart.managedservices.ServiceLifecycle;
import upstart.util.concurrent.CompletableFutures;
import io.upstartproject.avrocodec.AvroCodec;
import io.upstartproject.avrocodec.EnvelopeCodec;
import io.upstartproject.avrocodec.MemorySchemaRepo;
import io.upstartproject.avrocodec.SchemaRepo;
import org.apache.avro.specific.SpecificRecordBase;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Set;

/**
 * Arranges for {@link AvroCodec} and {@link EnvelopeCodec} instances to be available for {@link Inject injection}.
 * <p/>
 * Requires a binding for a {@link SchemaRepo} to also be configured elsewhere.
 *
 * @see MemorySchemaRepo
 */
public class AvroModule extends UpstartModule {

  public static Multibinder<AvroCodec.PackageKey> avroPackageBinder(Binder binder) {
    binder.install(new AvroModule());
    return Multibinder.newSetBinder(binder, AvroCodec.PackageKey.class);
  }

  public static void bindAvroFromPackage(Binder binder, String packageName) {
    bindAvroFromPackage(binder, AvroCodec.PackageKey.of(packageName, AvroModule.class.getClassLoader()));
  }

  public static void bindAvroFromPackage(Binder binder, AvroCodec.PackageKey packageKey) {
    avroPackageBinder(binder).addBinding().toInstance(packageKey);
  }

  public static void bindAvroFromRecordPackage(Binder binder, Class<? extends SpecificRecordBase> exampleClass) {
    bindAvroFromPackage(binder, AvroCodec.packageKey(exampleClass));
  }

  @Override
  protected void configure() {
    avroPackageBinder(binder());
    serviceManager().manage(AvroCodecService.class);
  }

  @Provides AvroCodec avroCodec(AvroCodecService service) {
    return service.getCodec();
  }

  @Provides @Singleton EnvelopeCodec envelopeCodec(AvroCodec avroCodec) {
    return new EnvelopeCodec(avroCodec);
  }

  @Singleton
  @ServiceLifecycle(ServiceLifecycle.Phase.Infrastructure)
  public static class AvroCodecService extends NotifyingService {
    private final AvroCodec codec;
    private final ComposableService codecService;
    private final Set<AvroCodec.PackageKey> packagesToRegister;

    @Inject
    AvroCodecService(Set<AvroCodec.PackageKey> packagesToRegister, SchemaRepo schemaRepo) {
      this.codec = new AvroCodec(schemaRepo);
      this.codecService = ComposableService.enhance(codec);
      this.packagesToRegister = packagesToRegister;
    }

    @Override
    protected void doStart() {
      startWith(codecService.start()
              .thenCompose(__ -> CompletableFutures.allOf(packagesToRegister.stream()
                      .map(codec::registerSpecificRecordSchemas))));
    }

    @Override
    protected void doStop() {
      stopWith(codecService.stop());
    }

    AvroCodec getCodec() {
      return codec;
    }
  }
}
