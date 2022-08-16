package io.upstartproject.avrocodec.dynamodb;

import io.upstartproject.avro.MessageEnvelope;
import io.upstartproject.avrocodec.AvroPublisher;
import io.upstartproject.avrocodec.AvroSchemaConflictException;
import io.upstartproject.avrocodec.SchemaDescriptor;
import io.upstartproject.avrocodec.SchemaRegistry;
import io.upstartproject.avrocodec.upstart.AvroPublicationModule;
import io.upstartproject.avrocodec.upstart.DataStore;
import org.apache.avro.Schema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import upstart.aws.test.dynamodb.LocalDynamoDbTest;
import upstart.config.UpstartModule;
import upstart.dynamodb.DynamoDbNamespace;
import upstart.log4j.test.SuppressLogs;
import upstart.test.ThreadPauseHelper;
import upstart.test.UpstartServiceTest;
import upstart.test.UpstartTestBuilder;
import upstart.util.concurrent.CompletableFutures;
import upstart.util.concurrent.Deadline;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static com.google.common.truth.Truth.assertThat;
import static org.awaitility.Awaitility.await;
import static upstart.test.truth.CompletableFutureSubject.assertThat;

@LocalDynamoDbTest
@UpstartServiceTest
class DynamoDbSchemaRegistryTest extends UpstartModule {
  public static final String TEST_DATASTORE = "test";
  private static final DataStore DATA_STORE = DataStore.Factory.dataStore(TEST_DATASTORE);
  @Override
  protected void configure() {
    install(new DynamoDbSchemaRegistry.DynamoDbSchemaRegistryModule(DATA_STORE, new DynamoDbNamespace("TEST")));
    AvroPublicationModule.bindAvroFromRecordPackage(binder(), DATA_STORE, MessageEnvelope.class);
  }

  @Inject @DataStore(TEST_DATASTORE) AvroPublisher avroPublisher;

  @Test
  void roundTrip() {
    avroPublisher.getPreRegisteredPacker(MessageEnvelope.class).schema();
  }

  @Nested
  class WithInterceptedRepo {
    private final ThreadPauseHelper pauseHelper = new ThreadPauseHelper(Deadline.withinSeconds(5));
    private DynamoDbTable<DynamoDbSchemaRegistry.SchemaTable.SchemaDocument> schemaTable;
    private CompletableFuture<Void> racingInsertion;

    @Inject DynamoRegistrySpy dynamoRepoSpy;

    @BeforeEach
    void injectRepoSpy(UpstartTestBuilder testBuilder, @Named("TEST.schemarepo") DynamoDbTable<DynamoDbSchemaRegistry.SchemaTable.SchemaDocument> schemaTable) {
      this.schemaTable = schemaTable;
      testBuilder.overrideBindings(binder -> {
        binder.bind(SchemaRegistry.class).annotatedWith(DataStore.Factory.dataStore(TEST_DATASTORE)).to(DynamoRegistrySpy.class);
        binder.bind(ThreadPauseHelper.class).toInstance(pauseHelper);
      });

      racingInsertion = pauseHelper.doWhenPaused(1, () -> {
        schemaTable.putItem(new DynamoDbSchemaRegistry.SchemaTable.SchemaDocument(SchemaDescriptor.of(MessageEnvelope.getClassSchema()), 0));
      });
    }

    @Test
    void insertionsAreIdempotent() {
      assertThat(racingInsertion).completedNormally();
    }

    @Test
    @SuppressLogs(AvroPublisher.class)
    void conflictsAreRejectedAndCleaned() throws InterruptedException {
      assertSchemaCount(1);

      ThreadPauseHelper.PendingPause pause = pauseHelper.requestPause(1);
      CompletableFuture<Void> resumed = pause.doWhenPaused(true, () -> {
        schemaTable.putItem(new DynamoDbSchemaRegistry.SchemaTable.SchemaDocument(
                SchemaDescriptor.of(INCOMPATIBLE_SCHEMA1),
                1
        ));
      });

      Schema rejectedSchema = new Schema.Parser().parse(INCOMPATIBLE_SCHEMA2);
      CompletableFuture<Void> failedRegistration = avroPublisher.ensureRegistered(rejectedSchema);
      resumed.join();
      assertThat(failedRegistration)
              .doneWithin(Deadline.withinSeconds(5))
              .completedExceptionallyWith(AvroSchemaConflictException.class);

      await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertSchemaCount(2));

      assertThat(dynamoRepoSpy.deletionCount.get()).isEqualTo(1);
    }

    private void assertSchemaCount(int expectedSize) {
      List<DynamoDbSchemaRegistry.SchemaTable.SchemaDocument> allSchemas = schemaTable.scan().items().stream().toList();
      assertThat(allSchemas).hasSize(expectedSize);
    }

    @Singleton
    static class DynamoRegistrySpy implements SchemaRegistry {
      private final SchemaRegistry realRepo;
      private final ThreadPauseHelper pauseHelper;
      AtomicInteger deletionCount = new AtomicInteger();

      @Inject
      DynamoRegistrySpy(@DataStore(TEST_DATASTORE) DynamoDbSchemaRegistry realRepo, ThreadPauseHelper pauseHelper) {
        this.realRepo = realRepo;
        this.pauseHelper = pauseHelper;
      }

      @Override
      public CompletableFuture<?> startUp(SchemaListener schemaListener) {
        return realRepo.startUp(schemaListener);
      }

      @Override
      public CompletableFuture<?> insert(List<? extends SchemaDescriptor> schemas) {
        return CompletableFutures.callSafely(() -> {
          pauseHelper.pauseIfRequested(Duration.ofSeconds(1));
          return realRepo.insert(schemas);
        });
      }

      @Override
      public CompletableFuture<?> delete(SchemaDescriptor schema) {
        return realRepo.delete(schema).whenComplete((v, t) -> deletionCount.incrementAndGet());
      }

      @Override
      public CompletableFuture<Void> refresh() {
        return realRepo.refresh();
      }

      @Override
      public CompletableFuture<?> shutDown() {
        return realRepo.shutDown();
      }
    }
  }

  static final String INCOMPATIBLE_SCHEMA1 = """
          {
            "namespace": "io.upstartproject.avro.test",
            "name": "IncompatibleTestRecord",
            "published": true,
            "type": "record",
            "fields": [
              {"name": "conflictingField", "type": "int", "default": 7}
            ]
          }
          """;
  static final String INCOMPATIBLE_SCHEMA2 = """
          {
            "namespace": "io.upstartproject.avro.test",
            "name": "IncompatibleTestRecord",
            "published": true,
            "type": "record",
            "fields": [
               {"name": "conflictingField", "type": "string", "default": ""}
            ]
          }
          """;
}