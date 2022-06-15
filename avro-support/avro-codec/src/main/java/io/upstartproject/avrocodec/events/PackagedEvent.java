package io.upstartproject.avrocodec.events;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.upstartproject.avrocodec.EnvelopeCodec;
import io.upstartproject.avrocodec.MessageMetadata;
import io.upstartproject.avrocodec.PackableRecord;
import io.upstartproject.avro.MessageEnvelope;
import upstart.util.collect.PairStream;
import upstart.util.SelfType;
import io.upstartproject.avrocodec.AvroCodec;
import org.immutables.value.Value;
import upstart.util.strings.RandomId;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * Encapsulates a fully-specified event-message, ready to be delivered to the specified {@link #topic}
 * via an {@link EventPublisher}
 * @see EventPublisher
 * @see EventLog
 * @see MessageEnvelope
 * @see EnvelopeCodec
 *
 */
@Value.Immutable
@Value.Style(overshadowImplementation = true)
public abstract class PackagedEvent {
  public static Builder builder() {
    return new Builder();
  }

  @JsonInclude(JsonInclude.Include.NON_EMPTY)
  @JsonProperty
  public abstract Optional<String> key();

  @Value.Default
  @JsonProperty
  public String uniqueId() {
    return RandomId.newRandomId();
  }

  @JsonProperty
  public abstract Instant timestamp();

  @JsonIgnore
  public abstract MessageMetadata metadata();

  @JsonProperty("record")
  public abstract PackableRecord<?> event();

  @JsonIgnore
  public abstract List<PackableRecord<?>> annotations();

  @Value.Lazy
  @JsonProperty("annotations")
  public Map<AvroCodec.RecordTypeFamily, PackableRecord<?>> annotationMap() {
    Set<String> duplicatedTypes = new HashSet<>();
    Map<AvroCodec.RecordTypeFamily, PackableRecord<?>> recordTypeFamilies = PairStream.withMappedKeys(
            annotations().stream(),
            PackableRecord::getRecordTypeFamily
    ).toImmutableMap((a, b) -> {
      duplicatedTypes.add(a.getRecordTypeFamily().getFullName());
      return a; // doesn't matter what we return, we'll throw a duplicated-type exception below
    });
    checkArgument(duplicatedTypes.isEmpty(), "PackagedEvent annotation-types must be unique; found duplicates: %s", duplicatedTypes);
    return recordTypeFamilies;
  }

  public MessageEnvelope toEnvelope(EnvelopeCodec codec) {
    return codec.buildMessageEnvelope(timestamp(), Optional.of(uniqueId()), event(), metadata(), annotations());
  }

  @Override
  public String toString() {
    return event().getRecordTypeFamily().getFullName() + "{uniqueId=" + uniqueId() + ", timestamp=" + timestamp() + ", metadata=" + metadata() + ", event=" + event().record() + "}";
  }

  @FunctionalInterface
  public interface Decorator {
    Builder decorate(Builder eventBuilder);

    default Decorator andThen(Decorator next) {
      return builder -> next.decorate(decorate(builder));
    }
  }

  public interface Decoratable<S extends Decoratable<S>> extends SelfType<S> {
    PackagedEvent.Decorator decorator();

    S overrideDecorator(PackagedEvent.Decorator eventDecorator);

    default S withAnnotation(PackableRecord<?> annotationRecord) {
      return decorated(builder -> builder.addAnnotations(annotationRecord));
    }

    default S decorated(PackagedEvent.Decorator nextDecorator) {
      return overrideDecorator(decorator().andThen(nextDecorator));
    }
  }

  public static class Builder extends ImmutablePackagedEvent.Builder { }
}
