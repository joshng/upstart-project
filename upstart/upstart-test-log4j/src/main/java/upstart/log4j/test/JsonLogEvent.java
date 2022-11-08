package upstart.log4j.test;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.immutables.value.Value;
import upstart.config.annotations.DeserializedImmutable;

import java.time.Instant;
import java.util.Optional;

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
