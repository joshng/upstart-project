package upstart.log4j;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.common.io.CharSource;
import com.google.common.truth.OptionalSubject;
import io.upstartproject.hojack.HojackConfigMapper;
import upstart.ExecutorServiceScheduler;
import upstart.config.UpstartModule;
import upstart.log.UpstartLogConfig;
import upstart.log.UpstartLogProvider;
import upstart.test.AfterInjection;
import upstart.test.UpstartTest;
import upstart.test.UpstartTestBuilder;
import upstart.test.systemStreams.CaptureSystemOut;
import upstart.test.systemStreams.SystemOutCaptor;
import upstart.util.LogLevel;
import com.typesafe.config.ConfigFactory;
import org.apache.log4j.helpers.LogLog;
import org.immutables.value.Value;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.io.IOException;
import java.time.Instant;
import java.util.Optional;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

@UpstartTest
@CaptureSystemOut
class JsonLog4jLayoutTest extends UpstartModule {
  private static final Logger LOG = LoggerFactory.getLogger(JsonLog4jLayoutTest.class);
  private static final String UNSERIALIZABLE_EXCEPTION_MESSAGE = "unserializable object for test";

  @Override
  protected void configure() {
    install(new ExecutorServiceScheduler.Module());
    bindConfig(UpstartLogConfig.class);
  }

  @BeforeEach
  void setupConfig(UpstartTestBuilder testBuilder) {
    testBuilder.overrideConfig(ConfigFactory.parseResources("config-templates/json-stdout-log.conf").atPath("upstart.log"));
    testBuilder.overrideConfig("upstart.log.levels.upstart", "warn"); // stifle normal startup noise
  }

  @Inject
  UpstartLogConfig logConfig;

  ObjectMapper objectMapper;
  @Inject
  UpstartLogProvider logProvider;

  @AfterInjection
  void applyLogConfig() {
    logConfig.apply();
    objectMapper = HojackConfigMapper.buildDefaultObjectMapper();
  }

  @Test
  void appenderWritesJson(SystemOutCaptor systemOutCaptor) throws IOException {
    LOG.error("just testing");

    JsonNode node = new ObjectMapper().readValue(systemOutCaptor.getCapturedBytes(), JsonNode.class);
    assertThat(node.get("message").textValue()).isEqualTo("just testing");
  }

  @Test
  void appenderWritesCustomPayload(SystemOutCaptor systemOutCaptor) throws IOException {
    TestPayload payload = TestPayload.builder()
            .string("outer")
            .nested(TestPayload.builder().string("inner").build())
            .build();

    logProvider.logWithPayload(LOG, LogLevel.Warn, payload, "with payload");

    JsonLogEvent logEvent = captureSingleLog(systemOutCaptor);

    TestPayload parsedPayload = objectMapper.convertValue(logEvent.payload().get(), TestPayload.class);

    assertThat(parsedPayload).isEqualTo(payload);
  }

  @Test
  void serializerCapturesPayloadExceptions(SystemOutCaptor systemOutCaptor) throws IOException {
    LogLog.setQuietMode(true); // temporarily disable log4j-internal error-log output
    try {
      logProvider.logWithPayload(LOG, LogLevel.Warn, TestPayload.UNSERIALIZABLE, "log with unserializable payload for test");
    } finally {
      LogLog.setQuietMode(false);
    }

    JsonLogEvent logEvent = captureSingleLog(systemOutCaptor);

    assertThatOptional(logEvent.payload(), "payload").isEmpty();

    assertThatOptional(logEvent.payload_exception(), "payload_exception").isPresent();
    JsonLogEvent.JsonExceptionInfo exceptionInfo = logEvent.payload_exception().get();
    assertThat(exceptionInfo.exception_class()).isEqualTo(JsonMappingException.class);
    assertThat(exceptionInfo.stacktrace()).contains(UNSERIALIZABLE_EXCEPTION_MESSAGE);
  }

  @Test
  void exceptionLogIsCaptured(SystemOutCaptor systemOutCaptor) throws IOException {
    String message = "<logged-exception>";
    String causeMessage = "<nested cause>";
    LOG.warn("test exception", new IllegalStateException(message, new IllegalArgumentException(causeMessage)));

    JsonLogEvent event = captureSingleLog(systemOutCaptor);

    assertThat(event.message()).isEqualTo("test exception");
    assertThatOptional(event.exception(), "Logged exception").isPresent();
    JsonLogEvent.JsonExceptionInfo exceptionInfo = event.exception().get();

    assertThat(exceptionInfo.exception_class()).isAssignableTo(IllegalStateException.class);
    assertThat(exceptionInfo.exception_message()).isEqualTo(message);
    assertThat(exceptionInfo.stacktrace()).contains(causeMessage);
  }

  @Test
  void context(SystemOutCaptor systemOutCaptor) throws IOException {
    TestPayload payload = TestPayload.builder()
            .string("logline")
            .build();

    logProvider.logWithPayload(LOG, LogLevel.Warn, payload, "with payload");

    JsonLogEvent logEvent = captureSingleLog(systemOutCaptor);
  }

  private static OptionalSubject assertThatOptional(Optional<?> optional, String subjectDescription) {
    return assertWithMessage(subjectDescription).about(OptionalSubject.optionals()).that(optional);
  }

  private JsonLogEvent captureSingleLog(SystemOutCaptor systemOutCaptor) throws IOException {

    String json = systemOutCaptor.getCapturedString();
    assertThat(json).endsWith("\n");
    assertThat(CharSource.wrap(json).readLines()).hasSize(1);

    return objectMapper.readValue(json, JsonLogEvent.class);
  }

  @Nested
  class WithJsonDisabled {
    @BeforeEach
    void overrideAppender(UpstartTestBuilder testBuilder) {
      testBuilder.overrideConfig(ConfigFactory.parseResources("config-templates/pattern-stdout-log.conf").atPath("upstart.log"));
    }

    @Test
    void jsonIsDisabled(SystemOutCaptor systemOutCaptor) {
      LOG.warn("not json");
      String message = systemOutCaptor.getCapturedString();
      assertThat(message).contains("not json");
      assertThat(message).doesNotContainMatch("^\\{");
    }
  }


  @Value.Immutable
  @JsonDeserialize(as = ImmutableJsonLogEvent.class)
  public interface JsonLogEvent {
    @JsonProperty("@timestamp")
    Instant timestamp();

    String message();

    Optional<Object> payload();
    Optional<Class<?>> payload_type();
    Optional<JsonExceptionInfo> payload_exception();

    Optional<JsonExceptionInfo> exception();
    String logger_name();
    String thread_name();
    String level();

    @JsonProperty("@version")
    int version();

    @Value.Immutable
    @JsonDeserialize(as = ImmutableJsonExceptionInfo.class)
    interface JsonExceptionInfo {
      Class<?> exception_class();
      String exception_message();
      String stacktrace();
    }
  }

  @Value.Immutable
  @JsonDeserialize(as = ImmutableTestPayload.class)
  public interface TestPayload {
    TestPayload UNSERIALIZABLE = builder().string("throw me").build();

    static ImmutableTestPayload.Builder builder() {
      return ImmutableTestPayload.builder();
    }

    @JsonProperty("bogus") default String inaccessible() {
      if (equals(UNSERIALIZABLE)) throw new IllegalStateException(UNSERIALIZABLE_EXCEPTION_MESSAGE);
      return null;
    }

    String string();
    Optional<TestPayload> nested();
  }

}