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
import java.util.stream.Stream;

import static com.google.common.truth.Truth.assertThat;
import static io.upstartproject.avrocodec.CompletableFutureAssertions.assertCompleted;
import static io.upstartproject.avrocodec.CompletableFutureAssertions.assertFailedWith;
import static io.upstartproject.avrocodec.CompletableFutureAssertions.assertPending;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static upstart.util.concurrent.CompletableFutures.nullFuture;


@ExtendWith(MockitoExtension.class)
class AvroCodecTest {
  @Test
  void comprehensiveEnvelopeRoundTrip() throws IOException {

    MemorySchemaRepo schemaRepo = new MemorySchemaRepo();
    AvroCodec avroCodec = new AvroCodec(schemaRepo);
    avroCodec.startAsync().awaitRunning();
    avroCodec.registerSpecificPackers(AvroCodec.PackageKey.fromRecordPackage(TestExceptionRecord.class)).join();
    EnvelopeCodec codec = new EnvelopeCodec(avroCodec).registerEnvelopeSchema().join();

    String exceptionMessage = "numbers";
    MessageMetadata metadata = MessageMetadata.builder()
            .application("test-app")
            .owner("test-owner")
            .environment("TEST")
            .deploymentStage(DeploymentStage.stage)
            .putTag("tag1", "value1").build();
    TestExceptionEvent event = new TestExceptionEvent(Instant.EPOCH, new TestExceptionRecord(exceptionMessage));
    TestAnnotation anno = new TestAnnotation(77L);

    PackableRecord<?> exceptionRecord = avroCodec.getPreRegisteredPacker(TestExceptionEvent.getClassSchema()).makePackable(event);
    PackableRecord<?> annotationRecord = avroCodec.getPreRegisteredPacker(anno.getSchema()).makePackable(anno);
    byte[] bytes = codec.packableMessageEnvelope(Instant.ofEpochMilli(99), Optional.empty(), exceptionRecord, metadata, annotationRecord).serialize();

    UnpackableMessageEnvelope env = codec.loadEnvelope(new ByteArrayInputStream(bytes)).join();
    assertThat(env.metadata()).isEqualTo(metadata);
    GenericRecord genericEvent = env.messageRecord().unpackGeneric();
    GenericRecord genericException = (GenericRecord) genericEvent.get("exception");

    assertThat(genericException.get("message")).isEqualTo(exceptionMessage);

    assertThat(env.annotationRecords().get(0).unpackGeneric().get("id")).isEqualTo(77L);
    assertThat(env.convertRequiredAnnotation(avroCodec.recordConverter(TestAnnotation.class))).isEqualTo(anno);

    SpecificRecordUnpacker<TestExceptionEvent> recordUnpacker = avroCodec.recordUnpacker(TestExceptionEvent.class);
    assertThat(recordUnpacker.unpack(
            codec.extractEnvelopeMessage(new ByteArrayInputStream(bytes)).join())
    ).isEqualTo(event);

    Schema alternateSchema = loadSchema("TestIssueView.avsc");
    GenericRecord eventView = env.messageRecord().unpackGeneric(alternateSchema);
    assertThat(eventView.getSchema()).isEqualTo(alternateSchema);
    assertThat(((GenericRecord)eventView.get("exception")).get("message").toString()).isEqualTo(event.getException().getMessage());

    assertThat(env.messageRecord().unpackSpecificOrGeneric(getClass().getClassLoader())).isEqualTo(event);
  }

  @Nested
  class WithMockSchemaRepo {

    @Captor ArgumentCaptor<SchemaRepo.SchemaListener> listenerCaptor;
    @Mock SchemaRepo mockRepo;
    private AvroCodec codec;
    private SchemaRepo.SchemaListener schemaListener;
    private SchemaDescriptor testRecordSchemaDescriptor;

    @BeforeEach
    void setupRepo() throws IOException {
      codec = new AvroCodec(mockRepo);

      when(mockRepo.startUp(listenerCaptor.capture())).thenReturn(nullFuture());
      when(mockRepo.refresh()).thenReturn(nullFuture());

      codec.startAsync().awaitRunning();

      schemaListener = listenerCaptor.getValue();
      testRecordSchemaDescriptor = SchemaDescriptor.of(loadSchema("IncompatibleTestRecord2.avsc"));
    }

    @SuppressLogs(value = AvroCodec.class, threshold = UpstartLogConfig.LogThreshold.ERROR)
    @Test
    void racingSchemasResolveCorrectly() throws IOException {
      when(mockRepo.insert(any())).thenReturn(nullFuture());
      when(mockRepo.delete(any())).thenReturn(nullFuture());
      Schema proposedConflictingSchema = loadSchema("IncompatibleTestRecord1.avsc");
      CompletableFuture<Void> racingFuture = codec.ensureRegistered(Stream.of(proposedConflictingSchema));
      assertPending(racingFuture);

      // arrange for a conflicting schema to arrive in the repo first
      schemaListener.onSchemaAdded(testRecordSchemaDescriptor);

      // now our proposed schema appears in the repo, losing the race
      SchemaDescriptor rejectedDescriptor = SchemaDescriptor.of(proposedConflictingSchema);
      schemaListener.onSchemaAdded(rejectedDescriptor);

      // confirm the repo is cleaned up by the offending party (non-critical, but good hygiene)
      verify(mockRepo).delete(rejectedDescriptor);

      assertFailedWith(AvroCodec.SchemaConflictException.class, codec.findRegisteredPacker(rejectedDescriptor.fingerprint()));

      schemaListener.onSchemaRemoved(rejectedDescriptor.fingerprint());

      assertFailedWith(AvroCodec.SchemaConflictException.class, codec.findRegisteredPacker(rejectedDescriptor.fingerprint()));

      assertFailedWith(AvroCodec.SchemaConflictException.class, racingFuture);

      assertThrows(AvroCodec.SchemaConflictException.class, () -> codec.getPreRegisteredPacker(proposedConflictingSchema));
    }

    @Test
    void schemasResolveAsynchronously() throws IOException {
      // to avoid using a code-generated java class here, read some json into a GenericRecord using our fake schema
      String json = "{\"conflictingField\":77}";
      GenericRecord record = new GenericDatumReader<GenericRecord>(testRecordSchemaDescriptor.schema())
              .read(null, DecoderFactory.get().jsonDecoder(testRecordSchemaDescriptor.schema(), json));

      // now write the record using a real AvroCodec that knows about the schema
      AvroCodec writerCodec = new AvroCodec(new MemorySchemaRepo());
      writerCodec.startAsync().awaitRunning();
      PackedRecord packedRecord = writerCodec.getOrRegisterPacker(testRecordSchemaDescriptor.schema())
              .join()
              .pack(record);

      // finally, ask our test-codec to unpack the record; this must wait until its SchemaRepo adds the schema
      CompletableFuture<UnpackableRecord> futureUnpackable = codec.toUnpackable(packedRecord);
      assertPending(futureUnpackable); // schema is unresolved, so future should still be pending

      schemaListener.onSchemaAdded(testRecordSchemaDescriptor);

      // now the future is completed, and we can unpack the record
      GenericRecord unpacked = assertCompleted(futureUnpackable).unpackGeneric();

      assertThat(unpacked.get("conflictingField")).isEqualTo(77);
    }
  }

  private static Schema loadSchema(String resourceName) throws IOException {
    return new Schema.Parser().parse(Resources.toString(Resources.getResource(resourceName), StandardCharsets.UTF_8));
  }
}