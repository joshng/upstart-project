package io.upstartproject.avrocodec;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.upstartproject.avro.DeploymentStage;
import org.immutables.value.Value;

import java.util.List;
import java.util.Map;

@Value.Immutable
@Value.Style(depluralize = true)
@JsonDeserialize(as = ImmutableMessageMetadata.class)
public interface MessageMetadata {
  static ImmutableMessageMetadata.Builder builder() {
    return ImmutableMessageMetadata.builder();
  }

  String application();
  String owner();
  String environment();
  DeploymentStage deploymentStage();
  Map<String, String> tags();
}
