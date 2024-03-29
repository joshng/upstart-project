package io.upstartproject.avrocodec;

import com.google.common.io.Resources;
import io.upstartproject.avro.DeploymentStage;
import io.upstartproject.avro.PackedRecord;
import io.upstartproject.avrocodec.test.avro.TestAnnotation;
import io.upstartproject.avrocodec.test.avro.TestExceptionEvent;
import io.upstartproject.avrocodec.test.avro.TestExceptionRecord;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.DecoderFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import upstart.log.UpstartLogConfig;
import upstart.log4j.test.SuppressLogs;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static upstart.test.truth.CompletableFutureSubject.assertThat;
import static upstart.util.concurrent.CompletableFutures.nullFuture;


@ExtendWith(MockitoExtension.class)
class AvroPublisherTest {
  @Test
  void comprehensiveEnvelopeRoundTrip() throws IOException {

    MemorySchemaRegistry schemaRepo = new MemorySchemaRegistry();
    AvroTaxonomy taxonomy = new AvroTaxonomy(schemaRepo);
    AvroPublisher avroPublisher = new AvroPublisher(taxonomy);
    taxonomy.startAsync().awaitRunning();
    avroPublisher.registerSpecificPackers(AvroPublisher.PackageKey.fromRecordPackage(TestExceptionRecord.class)).join();
    EnvelopePublisher envPublisher = new EnvelopePublisher(avroPublisher).registerEnvelopeSchema().join();
    EnvelopeDecoder envDecoder = new EnvelopeDecoder(new AvroDecoder(taxonomy));

    String exceptionMessage = "numbers";
    MessageMetadata metadata = MessageMetadata.builder()
            .application("test-app")
            .owner("test-owner")
            .environment("test")
            .deploymentStage(DeploymentStage.stage)
            .putTag("tag1", "value1").build();
    TestExceptionEvent event = new TestExceptionEvent(Instant.EPOCH, new TestExceptionRecord(exceptionMessage));
    TestAnnotation anno = new TestAnnotation(77L);

    PackableRecord<?> exceptionRecord = avroPublisher.getPreRegisteredPacker(TestExceptionEvent.getClassSchema()).makePackable(event);
    PackableRecord<?> annotationRecord = avroPublisher.getPreRegisteredPacker(anno.getSchema()).makePackable(anno);
    byte[] bytes = envPublisher.packableMessageEnvelope(Instant.ofEpochMilli(99), Optional.empty(), exceptionRecord, metadata, annotationRecord).serialize();

    UnpackableMessageEnvelope env = envDecoder.loadEnvelope(new ByteArrayInputStream(bytes)).join();
    assertThat(env.metadata()).isEqualTo(metadata);
    GenericRecord genericEvent = env.messageRecord().unpackGeneric();
    GenericRecord genericException = (GenericRecord) genericEvent.get("exception");

    assertThat(genericException.get("message")).isEqualTo(exceptionMessage);

    assertThat(env.annotationRecords().get(0).unpackGeneric().get("id")).isEqualTo(77L);
    AvroDecoder decoder = new AvroDecoder(taxonomy);
    assertThat(env.convertRequiredAnnotation(decoder.recordConverter(TestAnnotation.class))).isEqualTo(anno);

    SpecificRecordUnpacker<TestExceptionEvent> recordUnpacker = decoder.recordUnpacker(TestExceptionEvent.class);
    assertThat(recordUnpacker.unpack(
            envDecoder.extractEnvelopeMessage(new ByteArrayInputStream(bytes)).join())
    ).isEqualTo(event);

    Schema alternateSchema = loadSchema("TestIssueView.avsc");
    GenericRecord eventView = env.messageRecord().unpackGeneric(alternateSchema);
    assertThat(eventView.getSchema()).isEqualTo(alternateSchema);
    assertThat(((GenericRecord)eventView.get("exception")).get("message").toString()).isEqualTo(event.getException().getMessage());

    assertThat(env.messageRecord().unpackSpecificOrGeneric()).isEqualTo(event);
  }

  @Nested
  class WithMockSchemaRepo {

    @Captor ArgumentCaptor<SchemaRegistry.SchemaListener> listenerCaptor;
    @Mock SchemaRegistry mockRepo;
    private AvroPublisher codec;
    private SchemaRegistry.SchemaListener schemaListener;
    private SchemaDescriptor testRecordSchemaDescriptor;
    private AvroDecoder decoder;

    @BeforeEach
    void setupRepo() throws IOException {
      AvroTaxonomy taxonomy = new AvroTaxonomy(mockRepo);
      codec = new AvroPublisher(taxonomy);
      decoder = new AvroDecoder(taxonomy);

      when(mockRepo.startUp(listenerCaptor.capture())).thenReturn(nullFuture());
      when(mockRepo.refresh()).thenReturn(nullFuture());

      taxonomy.start().join();

      schemaListener = listenerCaptor.getValue();
      testRecordSchemaDescriptor = SchemaDescriptor.of(loadSchema("IncompatibleTestRecord2.avsc"));
    }

    @SuppressLogs(value = AvroPublisher.class, threshold = UpstartLogConfig.LogThreshold.ERROR)
    @Test
    void racingSchemasResolveCorrectly() throws IOException {
      when(mockRepo.insert(any())).thenReturn(nullFuture());
      when(mockRepo.delete(any())).thenReturn(nullFuture());
      Schema proposedConflictingSchema = loadSchema("IncompatibleTestRecord1.avsc");
      CompletableFuture<Void> racingFuture = codec.ensureRegistered(proposedConflictingSchema);
      assertThat(racingFuture).isNotDone();

      // arrange for a conflicting schema to arrive in the repo first
      schemaListener.onSchemaAdded(testRecordSchemaDescriptor);

      // now our proposed schema appears in the repo, losing the race
      SchemaDescriptor rejectedDescriptor = SchemaDescriptor.of(proposedConflictingSchema);
      schemaListener.onSchemaAdded(rejectedDescriptor);

      // confirm the repo is cleaned up by the offending party (non-critical, but good hygiene)
      verify(mockRepo).delete(rejectedDescriptor);

      assertThat(codec.findPreRegisteredPacker(rejectedDescriptor.fingerprint()))
              .failedWith(AvroSchemaConflictException.class);

      schemaListener.onSchemaRemoved(rejectedDescriptor.fingerprint());

      assertThat(codec.findPreRegisteredPacker(rejectedDescriptor.fingerprint()))
              .failedWith(AvroSchemaConflictException.class);

      assertThat(racingFuture).failedWith(AvroSchemaConflictException.class);

      assertThrows(AvroSchemaConflictException.class, () -> codec.getPreRegisteredPacker(proposedConflictingSchema));
    }

    @Test
    void schemasResolveAsynchronously() throws IOException {
      // to avoid using a code-generated java class here, read some json into a GenericRecord using our fake schema
      String json = "{\"conflictingField\":77}";
      GenericRecord record = new GenericDatumReader<GenericRecord>(testRecordSchemaDescriptor.schema())
              .read(null, DecoderFactory.get().jsonDecoder(testRecordSchemaDescriptor.schema(), json));

      // now write the record using a real AvroCodec that knows about the schema
      AvroTaxonomy taxonomy = new AvroTaxonomy(new MemorySchemaRegistry());
      AvroPublisher writerCodec = new AvroPublisher(taxonomy);
      taxonomy.startAsync().awaitRunning();
      PackedRecord packedRecord = writerCodec.getOrRegisterPacker(testRecordSchemaDescriptor.schema())
              .join()
              .pack(record);

      // finally, ask our test-codec to unpack the record; this must wait until its SchemaRepo adds the schema
      CompletableFuture<UnpackableRecord> futureUnpackable = decoder.toUnpackable(packedRecord);
      assertThat(futureUnpackable).isNotDone(); // schema is unresolved, so future should still be pending

      schemaListener.onSchemaAdded(testRecordSchemaDescriptor);

      assertThat(futureUnpackable).isDone();

      // now the future is completed, and we can unpack the record
      assertThat(futureUnpackable.join().unpackGeneric().get("conflictingField")).isEqualTo(77);
    }
  }

  private static Schema loadSchema(String resourceName) throws IOException {
    return new Schema.Parser().parse(Resources.toString(Resources.getResource(resourceName), StandardCharsets.UTF_8));
  }
}