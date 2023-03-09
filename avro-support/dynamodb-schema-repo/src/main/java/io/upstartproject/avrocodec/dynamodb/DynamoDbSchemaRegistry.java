package io.upstartproject.avrocodec.dynamodb;

import com.google.common.collect.Lists;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.inject.Binder;
import io.upstartproject.avrocodec.SchemaDescriptor;
import io.upstartproject.avrocodec.SchemaFingerprint;
import io.upstartproject.avrocodec.SchemaRegistry;
import io.upstartproject.avrocodec.upstart.AvroTaxonomyModule;
import io.upstartproject.avrocodec.upstart.DataStore;
import org.immutables.value.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.enhanced.dynamodb.Expression;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.internal.AttributeValues;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondaryPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;
import software.amazon.awssdk.enhanced.dynamodb.model.TransactPutItemEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.TransactWriteItemsEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.model.CancellationReason;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.TransactionCanceledException;
import upstart.config.ConfigKey;
import upstart.config.UpstartConfigBinder;
import upstart.config.UpstartModule;
import upstart.config.annotations.DeserializedImmutable;
import upstart.dynamodb.DynamoDbClientService;
import upstart.dynamodb.DynamoDbModule;
import upstart.dynamodb.DynamoDbNamespace;
import upstart.dynamodb.DynamoTableInitializer;
import upstart.guice.AnnotationKeyedPrivateModule;
import upstart.guice.PrivateBinding;
import upstart.provisioning.ProvisionedResource;
import upstart.util.collect.PairStream;
import upstart.util.concurrent.BlockingBoundedActor;
import upstart.util.concurrent.CompletableFutures;
import upstart.util.concurrent.Promise;
import upstart.util.exceptions.Exceptions;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.lang.annotation.Annotation;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

@Singleton
public class DynamoDbSchemaRegistry implements SchemaRegistry {
  private static final Logger LOG = LoggerFactory.getLogger(DynamoDbSchemaRegistry.class);
  private final SchemaTable table;
  private final BlockingBoundedActor actor = new BlockingBoundedActor(10);

  @Inject
  public DynamoDbSchemaRegistry(SchemaTable table) {
    this.table = table;
  }

  @Override
  public CompletableFuture<?> startUp(SchemaListener schemaListener) {
    table.setListener(schemaListener);
    return CompletableFutures.nullFuture();
  }

  @Override
  public CompletableFuture<?> insert(List<? extends SchemaDescriptor> schemas) {
    return actor.requestAsync(() -> table.insert(schemas), MoreExecutors.directExecutor());
  }

  @Override
  public CompletableFuture<?> delete(SchemaDescriptor schema) {
    return actor.requestAsync(() -> table.delete(schema), MoreExecutors.directExecutor());
  }

  @Override
  public CompletableFuture<Void> refresh() {
    return actor.requestAsync(table::refresh, MoreExecutors.directExecutor());
  }

  @Override
  public CompletableFuture<?> shutDown() {
    return CompletableFutures.nullFuture();
  }

  @Singleton
  public static class SchemaTable extends DynamoTableInitializer<SchemaTable.SchemaDocument> {

    public static final String FINGERPRINT_IDX = "byFingerprint";
    public static final Expression WHERE_NOT_EXISTS = Expression.builder().expression("attribute_not_exists(seqNo)").build();
    public static final Predicate<CancellationReason> CONDITIONAL_CHECK_FAILED = reason -> reason.code().equals("ConditionalCheckFailed");
    private final Map<SchemaDescriptor, Integer> knownSchemas = new ConcurrentHashMap<>();
    private final String namespace;
    private volatile int latestObservedSeqNo = -1;
    private SchemaListener listener;

    @Inject
    public SchemaTable(
            DynamoDbRegistryConfig config,
            DynamoDbClientService db,
            @PrivateBinding DynamoDbNamespace namespace
    ) {
      super(config.repoTableNameSuffix(), SchemaDocument.class, db, namespace);
      this.namespace = namespace.namespace();
    }

    public void setListener(SchemaListener listener) {
      this.listener = listener;
    }

    public CompletableFuture<Void> insert(List<? extends SchemaDescriptor> schemas) {
      List<? extends List<? extends SchemaDescriptor>> pages = Lists.partition(
              schemas,
              DynamoDbClientService.MAX_ITEMS_PER_DYNAMODB_BATCH
      );
      return CompletableFutures.applyInSequence(
              pages.stream(),
              this::insertPage
      ).thenReplaceFuture(this::refresh);
    }

    private Promise<Void> insertPage(List<? extends SchemaDescriptor> page) {
      TransactWriteItemsEnhancedRequest.Builder tx = TransactWriteItemsEnhancedRequest.builder();
      int seqNo = latestObservedSeqNo + 1;
      TransactPutItemEnhancedRequest.Builder<SchemaDocument> req = TransactPutItemEnhancedRequest
              .builder(SchemaDocument.class)
              .conditionExpression(WHERE_NOT_EXISTS);
      Map<SchemaDescriptor, Integer> newSchemas = new HashMap<>();
      for (SchemaDescriptor schema : page) {
        if (!knownSchemas.containsKey(schema)) {
          SchemaDocument item = new SchemaDocument(schema, seqNo++);
          tx.addPutItem(table, req.item(item).build());
          newSchemas.put(schema, item.getSeqNo());
        }
      }

      return newSchemas.isEmpty()
              ? CompletableFutures.nullFuture()
              : Promise.of(enhancedClient().transactWriteItems(tx.build()))
                      .thenRun(() -> {
                        latestObservedSeqNo += newSchemas.size();
                        knownSchemas.putAll(newSchemas);
                        page.forEach(listener::onSchemaAdded);
                      }).recoverCompose(
                              TransactionCanceledException.class,
                              e -> {
                                if (e.hasCancellationReasons() && e.cancellationReasons().stream().allMatch(
                                        CONDITIONAL_CHECK_FAILED)) {
                                  return refresh().thenReplaceFuture(() -> insertPage(page));
                                } else {
                                  throw Exceptions.throwUnchecked(e);
                                }
                              }
                      );
    }

    public CompletableFuture<Boolean> insert(SchemaDescriptor schemaDescriptor, int seqNo) {
      return Promise.of(table().putItem(b -> b.conditionExpression(WHERE_NOT_EXISTS)
              .item(new SchemaDocument(schemaDescriptor, seqNo))))
              .thenReplace(true)
              .recover(ConditionalCheckFailedException.class, e -> false);
    }

    public PairStream<SchemaDescriptor, Integer> getKnownSchemas() {
      return PairStream.of(knownSchemas);
    }

    public Promise<Void> refresh() {
      return Promise.of(itemFlux(table.scan(b -> {
        b.consistentRead(true);
        if (latestObservedSeqNo >= 0) {
          // so verbose ... does anyone at amazon actually work with these APIs?
          var filter = Expression.builder()
                  .expression("#seqNo > :seqNo")
                  .expressionNames(Map.of("#seqNo", "seqNo"))
                  .expressionValues(Map.of(":seqNo", AttributeValues.numberValue(latestObservedSeqNo)))
                  .build();
          b.filterExpression(filter);
        }
      })).reduce(0, (ignored, item) -> {
        int seqNo = item.getSeqNo();
        assert seqNo > latestObservedSeqNo : "SeqNo was out of order: " + seqNo + " <= " + latestObservedSeqNo;
        latestObservedSeqNo = seqNo;
        SchemaDescriptor descriptor = item.toSchemaDescriptor();
        if (knownSchemas.putIfAbsent(descriptor, seqNo) == null) {
          listener.onSchemaAdded(descriptor);
        }
        return ignored;
      }).then().toFuture());
    }

    public CompletableFuture<Void> delete(SchemaDescriptor schema) {
      Integer seqNo = knownSchemas.remove(schema);
      if (seqNo == null) {
        LOG.warn("Tried to delete unknown schema: {}", schema);
        return CompletableFutures.nullFuture();
      }
      return Promise.of(table().deleteItem(SchemaDocument.sortKey(seqNo))).toVoid();
    }

    @Override
    public String serviceName() {
      return "SchemaTable{" + namespace + "}";
    }

    @DynamoDbBean
    public static class SchemaDocument {
      private static final String GLOBAL_PARTITION_KEY_VALUE = "S";
      private String schema;
      private int seqNo;
      private SchemaFingerprint fp;

      public SchemaDocument(SchemaDescriptor schemaDescriptor, int seqNo) {
        fp = schemaDescriptor.fingerprint();
        schema = schemaDescriptor.schema().toString();
        this.seqNo = seqNo;
      }

      public SchemaDocument() {
      }

      private static Key sortKey(@Nonnull Integer seqNo) {
        return Key.builder().partitionValue(GLOBAL_PARTITION_KEY_VALUE).sortValue(seqNo).build();
      }

      @DynamoDbPartitionKey
      public String getPK() {
        return GLOBAL_PARTITION_KEY_VALUE;
      }

      public void setPK(String PK) {
        checkArgument(PK.equals(GLOBAL_PARTITION_KEY_VALUE), "Unexpected primary key: %s", PK);
      }

      @DynamoDbSortKey
      public int getSeqNo() {
        return seqNo;
      }

      public void setSeqNo(int seqNo) {
        this.seqNo = seqNo;
      }

      @DynamoDbSecondaryPartitionKey(indexNames = FINGERPRINT_IDX)
      public long getFp() {
        return fp.value();
      }

      public void setFp(long fp) {
        this.fp = SchemaFingerprint.of(fp);
      }

      public String getSchema() {
        return schema;
      }

      public void setSchema(String schema) {
        this.schema = schema;
      }

      public SchemaDescriptor toSchemaDescriptor() {
        SchemaDescriptor descriptor = SchemaDescriptor.of(schema);
        checkState(descriptor.fingerprint() == fp, "Fingerprint mismatch: %s != %s", descriptor.fingerprint(), fp);
        return descriptor;
      }

      @Override
      public String toString() {
        return "SchemaDocument{" +
                "seqNo=" + seqNo +
                ", schema='" + schema + '\'' +
                '}';
      }
    }
  }

  public static class DynamoDbSchemaRegistryModule extends UpstartModule {
    private final Annotation annotation;
    private final DynamoDbNamespace namespace;
    private final DynamoDbRegistryConfig config;

    public DynamoDbSchemaRegistryModule(Binder binder, DataStore annotation, DynamoDbNamespace namespace) {
      this(annotation, namespace, DynamoDbRegistryConfig.bind(binder, annotation));
    }

    public DynamoDbSchemaRegistryModule(Annotation annotation,
                                        DynamoDbNamespace namespace,
                                        DynamoDbRegistryConfig config
    ) {
      super(annotation, namespace, config);
      this.annotation = annotation;
      this.namespace = namespace;
      this.config = config;
    }

    @Override
    protected void configure() {
      install(new AvroTaxonomyModule(annotation));
      install(new DynamoDbModule());

      AnnotationKeyedPrivateModule privateModule = new AnnotationKeyedPrivateModule(
              annotation,
              SchemaRegistry.class,
              DynamoDbSchemaRegistry.class,
              SchemaTable.class
      ) {
        @Override
        protected void configurePrivateScope() {
          bind(DynamoDbRegistryConfig.class).toInstance(config);
          bind(SchemaRegistry.class).to(DynamoDbSchemaRegistry.class);
          bindPrivateBinding(DynamoDbNamespace.class).toInstance(namespace);
        }
      };
      install(privateModule);
      ProvisionedResource.bindProvisionedResource(binder(), privateModule.annotatedKey(SchemaTable.class));
    }
  }

  @DeserializedImmutable
  public interface DynamoDbRegistryConfig {
    static DynamoDbRegistryConfig bind(Binder binder, DataStore dataStore) {
      return bind(binder, "upstart.avroCodec.dynamoDbSchemaRepo." + dataStore.value(), dataStore);
    }

    static DynamoDbRegistryConfig bind(Binder binder, String configPath, Annotation dataStoreAnnotation) {
      return UpstartConfigBinder.get().bindConfig(
              binder,
              ConfigKey.of(configPath, DynamoDbRegistryConfig.class),
              com.google.inject.Key.get(DynamoDbRegistryConfig.class, dataStoreAnnotation)
      );
    }

    @Value.Default
    default String repoTableNameSuffix() {
      return "schemarepo";
    }
  }
}
