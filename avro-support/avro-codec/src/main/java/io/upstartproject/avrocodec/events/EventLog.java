package io.upstartproject.avrocodec.events;

import io.upstartproject.avrocodec.PackableRecord;
import upstart.util.LogLevel;
import upstart.util.concurrent.CompletableFutures;

import java.util.concurrent.CompletableFuture;

public abstract class EventLog implements PackagedEvent.Decorator, PackagedEvent.Decoratable<EventLog> {

  /**
   * Invokes the given eventInitializer to build an event (supplying it with a builder that has been decorated by
   * all decorators registered with this EventLog), then logs the event with the provided {@link LogLevel},
   * and publishes it to any registered {@link PackagedEventSink sinks}.
   */
  public CompletableFuture<?> publish(LogLevel diagnosticLogLevel, PackagedEvent.Decorator eventInitializer) {
    return CompletableFutures.callSafely(() -> directPublisher()
            .publish(diagnosticLogLevel, eventInitializer.decorate(decorate(PackagedEvent.builder())).build()));
  }

  /**
   * Builds a {@link PackagedEvent} with the given eventRecord as its {@link PackagedEvent#event} (decorated by all
   * decorators registered with this EventLog), logs it with the provided {@link LogLevel},
   * and publishes it to any registered {@link PackagedEventSink sinks}
   */
  public CompletableFuture<?> publishRecord(LogLevel diagnosticLogLevel, PackableRecord<?> eventRecord) {
    return publish(diagnosticLogLevel, builder -> builder.event(eventRecord));
  }

  public abstract CompletableFuture<?> flush();

  protected abstract EventPublisher directPublisher();

  @Override
  public EventLog overrideDecorator(PackagedEvent.Decorator eventDecorator) {
    return new DecoratedEventLog(directPublisher(), eventDecorator);
  }

  private static class DecoratedEventLog extends EventLog {
    private final EventPublisher publisher;
    private final PackagedEvent.Decorator decorator;

    DecoratedEventLog(EventPublisher publisher, PackagedEvent.Decorator decorator) {
      this.publisher = publisher;
      this.decorator = decorator;
    }

    @Override
    protected EventPublisher directPublisher() {
      return publisher;
    }

    @Override
    public CompletableFuture<?> flush() {
      return publisher.flush();
    }

    @Override
    public PackagedEvent.Builder decorate(PackagedEvent.Builder eventBuilder) {
      return decorator.decorate(eventBuilder);
    }

    @Override
    public PackagedEvent.Decorator decorator() {
      return decorator;
    }
  }
}
