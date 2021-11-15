package io.upstartproject.avrocodec.events;

import io.upstartproject.avro.MessageEnvelope;
import io.upstartproject.avrocodec.MessageMetadata;
import upstart.util.LogLevel;
import upstart.util.concurrent.CompletableFutures;
import io.upstartproject.avrocodec.EnvelopeCodec;

import javax.inject.Inject;
import java.time.Clock;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * A utility for delivering {@link PackagedEvent events} to a set of {@link PackagedEventSink}s, serialized as {@link MessageEnvelope}.
 * @see EnvelopeCodec
 * @see EventLog
 */
public class EventPublisher extends EventLog {
  private final EnvelopeCodec codec;
  private final MessageMetadata metadata;
  private final Set<PackagedEventSink> sinks;
  private final Clock clock;
  private final Set<PackagedEvent.Decorator> decorators;

  @Inject
  public EventPublisher(
          EnvelopeCodec codec,
          MessageMetadata metadata,
          Clock clock,
          Set<PackagedEventSink> sinks,
          Set<PackagedEvent.Decorator> decorators
  ) {
    this.codec = codec;
    this.metadata = metadata;
    this.sinks = sinks;
    this.clock = clock;
    this.decorators = decorators;
  }

  public CompletableFuture<?> publish(LogLevel diagnosticLogLevel, PackagedEvent event) {
    MessageEnvelope envelope = event.toEnvelope(codec);
    byte[] value = codec.getSerializedBytes(envelope);
    return CompletableFutures.allOf(sinks.stream().map(sink -> sink.publish(diagnosticLogLevel, event, value)));
  }

  @Override
  public void flush() {
    for (PackagedEventSink sink : sinks) {
      sink.flush();
    }
  }

  @Override
  public PackagedEvent.Builder decorate(PackagedEvent.Builder eventBuilder) {
    eventBuilder.timestamp(clock.instant())
            .metadata(metadata);
    for (PackagedEvent.Decorator decorator : decorators) {
      eventBuilder = decorator.decorate(eventBuilder);
    }
    return eventBuilder;
  }

  @Override
  public EventPublisher directPublisher() {
    return this;
  }

  @Override
  public PackagedEvent.Decorator decorator() {
    return this;
  }
}
