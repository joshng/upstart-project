package upstart.telemetry;

import com.google.common.collect.MoreCollectors;
import io.upstartproject.avro.event.ExceptionEvent;
import io.upstartproject.avro.event.GitContextAnnotation;
import io.upstartproject.avrocodec.MemorySchemaRegistry;
import io.upstartproject.avrocodec.SchemaRegistry;
import io.upstartproject.avrocodec.UnpackableMessageEnvelope;
import io.upstartproject.avrocodec.UnpackableRecord;
import io.upstartproject.avrocodec.upstart.AvroPublicationModule;
import io.upstartproject.avrocodec.upstart.EventLogger;
import org.apache.avro.generic.GenericRecord;
import org.junit.jupiter.api.Test;
import upstart.config.UpstartModule;
import upstart.telemetry.test.CapturingEventSink;
import upstart.test.UpstartLibraryServiceTest;
import upstart.util.LogLevel;

import javax.inject.Inject;

import static com.google.common.truth.Truth.assertThat;

@UpstartLibraryServiceTest(GitContextAnnotator.Module.class)
public class GitContextAnnotatorTest extends UpstartModule {
  @Inject EventLogger<ExceptionEvent> exceptionLogger;
  @Inject CapturingEventSink eventSink;

  @Override
  protected void configure() {
    AvroPublicationModule.publishAvroFromRecordPackage(binder(), EventLogModule.TELEMETRY_DATA_STORE, GitContextAnnotation.class);
    install(new CapturingEventSink.Module());
    bind(SchemaRegistry.class).annotatedWith(EventLogModule.TELEMETRY_DATA_STORE).toInstance(new MemorySchemaRegistry());
  }

  @Test
  public void test() {
    exceptionLogger.publish(LogLevel.Debug, new ExceptionRecordBuilder().toExceptionEvent(new Exception("test"))).join();

    UnpackableMessageEnvelope envelope = eventSink.findEvents(ExceptionEvent.class)
            .findFirst()
            .orElseThrow();
    UnpackableRecord unpackableGitContext = envelope
            .findAnnotationRecords(eventSink.typeFamily(GitContextAnnotation.class))
            .collect(MoreCollectors.onlyElement());
    GenericRecord gitContext = unpackableGitContext.unpackSpecificOrGeneric();
    assertThat(gitContext).isInstanceOf(GitContextAnnotation.class);
  }

}
