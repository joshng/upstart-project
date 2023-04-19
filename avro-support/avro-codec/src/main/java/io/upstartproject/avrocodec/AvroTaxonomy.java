package io.upstartproject.avrocodec;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import org.apache.avro.specific.SpecificRecordBase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import upstart.util.concurrent.Promise;
import upstart.util.concurrent.ShutdownException;
import upstart.util.concurrent.services.NotifyingService;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

@Singleton
public class AvroTaxonomy extends NotifyingService {
  private static final Logger LOG = LoggerFactory.getLogger(AvroTaxonomy.class);
  private static final TaxonomyListener NULL_LISTENER = new TaxonomyListener() {
    @Override public void onSchemaAdded(SchemaDescriptor schema, RecordTypeFamily.RegistrationResult registrationResult) { }
    @Override public void onSchemaRemoved(SchemaFingerprint fingerprint) { }
    @Override public void onShutdown() { }
  };

  private final SchemaRegistry registry;
  private final LoadingCache<String, RecordTypeFamily> typesByFullName = CacheBuilder.newBuilder()
          .build(CacheLoader.from(RecordTypeFamily::new));

  private final LoadingCache<SchemaFingerprint, RegistrationRequest> knownFingerprints = CacheBuilder.newBuilder()
          .build(CacheLoader.from(RegistrationRequest::new));

  private TaxonomyListener listener = NULL_LISTENER;

  @Inject
  public AvroTaxonomy(SchemaRegistry registry) {
    this.registry = registry;
  }

  synchronized void setListener(TaxonomyListener listener) {
    checkState(state() == State.NEW, "Taxonomy listener can only be set before service is started");
    checkState(this.listener == NULL_LISTENER, "Taxonomy listener can only be set once");
    this.listener = listener;
  }

  public Stream<RecordTypeFamily> getAllRegisteredRecordTypes() {
    checkRunning();
    return typesByFullName.asMap().values().stream();
  }

  public RecordTypeFamily findTypeFamily(Class<? extends SpecificRecordBase> recordClass) {
    return findTypeFamily(recordClass.getName());
  }

  public Promise<RecordTypeFamily.RegistrationResult> findSchemaDescriptor(SchemaFingerprint fingerprint) {
    return knownFingerprints.getUnchecked(fingerprint).ensureRequested();
  }

  public RecordTypeFamily findTypeFamily(String fullName) {
    checkRunning();
    RecordTypeFamily family = typesByFullName.getIfPresent(fullName);
    checkArgument(family != null, "Unrecognized record-type name", fullName);
    return family;
  }

  private void checkRunning() {
    State state = state();
    checkState(state == State.RUNNING, "Taxonomy was not running", state);
  }

  RecordTypeFamily findOrCreateTypeFamily(Class<? extends SpecificRecordBase> recordClass) {
    return findOrCreateTypeFamily(recordClass.getName());
  }


  RecordTypeFamily findOrCreateTypeFamily(String fullName) {
    return typesByFullName.getUnchecked(fullName);
  }

  @Override
  protected synchronized void doStart() {
    registry.startUp(new SchemaRegistry.SchemaListener() {
              @Override
              public void onSchemaAdded(SchemaDescriptor schema) {
                RecordTypeFamily.RegistrationResult registrationResult = findOrCreateTypeFamily(schema.fullName())
                        .addVersion(schema);
                knownFingerprints.getUnchecked(schema.fingerprint()).registrationPromise.complete(registrationResult);
                listener.onSchemaAdded(schema, registrationResult);
              }

              @Override
              public void onSchemaRemoved(SchemaFingerprint fingerprint) {
                knownFingerprints.invalidate(fingerprint);
                listener.onSchemaRemoved(fingerprint);
              }
            }).thenCompose(ignored -> refresh())
            .whenComplete((ignored, ex) -> {
              if (ex == null) {
                notifyStarted();
              } else {
                notifyFailed(ex);
              }
            });
  }

  @Override
  protected void doStop() {
    registry.shutDown().whenComplete((__, e) -> {
      knownFingerprints.asMap().forEach((fingerprint, request) -> {
        if (!request.registrationPromise.isDone()) {
          request.registrationPromise.completeExceptionally(new ShutdownException("AvroTaxonomy was shut down while awaiting schema: " + fingerprint.hexValue()));
        }
      });
      try {
        listener.onShutdown();
      } catch (Throwable ex) {
        if (e == null) {
          e = ex;
        } else {
          e.addSuppressed(ex);
        }
      }
      if (e == null) {
        notifyStopped();
      } else {
        notifyFailed(e);
      }
    });
  }

  public CompletableFuture<?> insert(List<? extends SchemaDescriptor> schemas) {
    checkRunning();
    return failWith(registry.insert(schemas));
  }

  public CompletableFuture<?> delete(SchemaDescriptor schema) {
    checkRunning();
    knownFingerprints.invalidate(schema.fingerprint());
    return registry.delete(schema);
  }

  public CompletableFuture<Void> refresh() {
    return registry.refresh();
  }

  private class RegistrationRequest {
    private final AtomicBoolean issued = new AtomicBoolean(false);
    private final SchemaFingerprint fingerprint;
    private final Promise<RecordTypeFamily.RegistrationResult> registrationPromise = new Promise<>();

    public RegistrationRequest(SchemaFingerprint fingerprint) {
      this.fingerprint = fingerprint;
    }

    Promise<RecordTypeFamily.RegistrationResult> ensureRequested() {
      if (!issued.getAndSet(true) && !registrationPromise.isDone()) {
        Promise<Void> refresh = Promise.of(registry.refresh());
        if (!registrationPromise.isDone()) {
          LOG.warn(
                  "Awaiting arrival of unrecognized schema-fingerprint: {} from registry {}",
                  fingerprint.hexValue(),
                  registry
          );
          registrationPromise.thenAccept(result -> LOG.warn("Awaited schema arrived, {}: {}", fingerprint.hexValue(), result.typeFamily()));
          refresh.uponCompletion(() -> {
            if (!registrationPromise.isCompletedNormally()) LOG.error("Awaited schema failed to arrive: {}", fingerprint.hexValue());
          });
        }
      }
      return registrationPromise;
    }
  }

  interface TaxonomyListener {
    void onSchemaAdded(SchemaDescriptor schema, RecordTypeFamily.RegistrationResult registrationResult);

    void onSchemaRemoved(SchemaFingerprint fingerprint);

    void onShutdown();
  }
}
