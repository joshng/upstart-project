package io.upstartproject.avrocodec.s3;

import upstart.util.collect.PairStream;
import upstart.util.collect.PersistentList;
import upstart.util.concurrent.CompletableFutures;
import upstart.util.concurrent.FutureCell;
import io.upstartproject.avrocodec.BaseSchemaRepo;
import io.upstartproject.avrocodec.SchemaDescriptor;
import org.immutables.value.Value;
import software.amazon.awssdk.core.BytesWrapper;
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.core.async.AsyncResponseTransformer;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.BucketVersioningStatus;
import software.amazon.awssdk.services.s3.model.ListObjectVersionsRequest;
import software.amazon.awssdk.services.s3.model.ListObjectVersionsResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.ObjectVersion;
import upstart.util.concurrent.Promise;

import javax.inject.Inject;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import static com.google.common.base.Preconditions.checkState;

public class S3SchemaRepo extends BaseSchemaRepo {
  private final S3RepoConfig config;
  private final Supplier<S3AsyncClient> clientSupplier;
  private final String schemaPathPrefix;
  private final Set<S3SchemaEntry> reportedVersions = ConcurrentHashMap.newKeySet();
  private final FutureCell<Void> refreshCell = FutureCell.<Void>builder().build(CompletableFutures.nullFuture());
  private S3AsyncClient client;

  @Inject
  public S3SchemaRepo(S3RepoConfig config, Supplier<S3AsyncClient> clientSupplier) {
    this.config = config;
    // TODO: should we confirm a leading slash here?
    this.clientSupplier = clientSupplier;
    schemaPathPrefix = config.repoPath().endsWith("/") ? config.repoPath() : config.repoPath() + "/";
  }

  @Override
  public CompletableFuture<?> startUpAsync() {
    return CompletableFutures.sequence(supplyOnDedicatedThread("STARTING", () -> {
      client = clientSupplier.get();
      return client.getBucketVersioning(b -> b.bucket(config.repoBucket()))
              .thenAccept(versioningResponse -> {
                BucketVersioningStatus status = versioningResponse.status();
                checkState(status == BucketVersioningStatus.ENABLED,
                        "S3SchemaRepo requires S3 object-versioning, but bucket %s does not have versioning enabled (status=%s)", config.repoBucket(), status);
              });
    }));
  }

  @Override
  public CompletableFuture<?> insert(List<? extends SchemaDescriptor> schemas) {
    // must take care to preserve the order of the schemas for each type, so we issue those putObjects sequentially
    return CompletableFutures.allOf(PairStream.withMappedKeys(schemas.stream(), SchemaDescriptor::fullName)
            .toImmutableListMultimap().asMap().values().stream()
            .map(typeSchemas -> CompletableFutures.applyInSequence(typeSchemas.stream(),
                    schema -> client.putObject(
                            b -> b.bucket(config.repoBucket())
                                    .key(schemaPathPrefix + schema.fullName()),
                            AsyncRequestBody.fromString(schema.schema().toString())
                    )
            ))
    ).thenCompose(ignored -> refresh());
  }

  @Override
  public CompletableFuture<?> delete(SchemaDescriptor schema) {
    return reportedVersions.stream()
            .filter(entry -> entry.loadDescriptor().isDone() && entry.loadDescriptor().join().equals(schema))
            .findFirst()
            .map(S3SchemaEntry::delete)
            .orElseThrow(() -> new IllegalStateException("Tried to delete an unrecognized schema: " + schema));
  }

  @Override
  public CompletableFuture<Void> refresh() {
    return refreshCell.visitAsync(ignored -> {
      ListObjectVersionsRequest request = ListObjectVersionsRequest.builder().bucket(config.repoBucket()).prefix(schemaPathPrefix).build();
      return fetchVersionList(request, PersistentList.nil())
              .thenCompose(responses ->
                      responses.stream()
                              .flatMap(r -> r.versions().stream())
                              .map(this::buildEntry)
                              .filter(reportedVersions::add)
                              .sorted(S3SchemaEntry.COMPARATOR) // sort to report all versions in their deterministic creation-order
                              .map(S3SchemaEntry::loadDescriptor)
                              .reduce(
                                      (CompletableFuture<Void>) CompletableFutures.<Void>nullFuture(),
                                      (accum, descriptorFuture) -> descriptorFuture.thenCombine(accum, (descriptor, ignored2) -> {
                                        notifySchemaAdded(descriptor);
                                        return null;
                                      }),
                                      CompletableFuture::allOf
                              ));
    });
  }

  private CompletableFuture<List<ListObjectVersionsResponse>> fetchVersionList(ListObjectVersionsRequest request, PersistentList<ListObjectVersionsResponse> response) {
    return client.listObjectVersions(request)
            .thenCompose(versions -> {
              PersistentList<ListObjectVersionsResponse> newResponse = response.with(versions);
              if (versions.isTruncated()) {
                return fetchVersionList(request.copy(b -> b.keyMarker(versions.nextKeyMarker()).versionIdMarker(versions.nextVersionIdMarker())), newResponse);
              } else {
                return CompletableFuture.completedFuture(newResponse);
              }
            });
  }

  @Override
  public CompletableFuture<?> shutDown() {
    return runOnDedicatedThread("SHUTDOWN", client::close);
  }

  private S3SchemaEntry buildEntry(ObjectVersion version) {
    return S3SchemaEntry.of(version, client, config);
  }

  @Value.Immutable
  interface S3SchemaEntry {
    Comparator<S3SchemaEntry> COMPARATOR = Comparator.comparing(S3SchemaEntry::lastModified).thenComparing(S3SchemaEntry::versionId);

    static S3SchemaEntry of(ObjectVersion version, S3AsyncClient client, S3RepoConfig config) {
      return ImmutableS3SchemaEntry.builder()
              .versionId(version.versionId())
              .key(version.key())
              .lastModified(version.lastModified())
              .client(client)
              .config(config)
              .build();
    }

    String versionId();
    String key();

    @Value.Auxiliary
    Instant lastModified();

    @Value.Auxiliary
    S3AsyncClient client();
    @Value.Auxiliary
    S3RepoConfig config();

    @Value.Lazy
    default CompletableFuture<SchemaDescriptor> loadDescriptor() {
      return client().getObject(b -> b.bucket(config().repoBucket()).key(key()).versionId(versionId()), AsyncResponseTransformer.toBytes())
              .thenApply(BytesWrapper::asByteArrayUnsafe)
              .thenApply(SchemaDescriptor::of);
    }

    @Value.Lazy
    default CompletableFuture<?> delete() {
      return CompletableFutures.recover(client().deleteObjects(b -> b.bucket(config().repoBucket())
              .delete(d -> d.objects(ObjectIdentifier.builder().key(key()).versionId(versionId()).build()))
      ), NoSuchKeyException.class, e -> null);
    }
  }

  public interface S3RepoConfig {
    String repoBucket();
    String repoPath();
  }
}
