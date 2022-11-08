package io.upstartproject.avrocodec.upstart;

import io.upstartproject.avrocodec.events.PackagedEvent;
import org.apache.avro.specific.SpecificRecordBase;

import java.util.Optional;

public abstract class BaseDynamicEventDecorator<T extends SpecificRecordBase> implements PackagedEvent.Decorator {
  private final DynamicEventAnnotator<T> annotator;

  protected BaseDynamicEventDecorator(DynamicEventAnnotator<T> annotator) {
    this.annotator = annotator;
  }

  @Override
  public PackagedEvent.Builder decorate(PackagedEvent.Builder eventBuilder) {
    return getEventAnnotation(eventBuilder)
            .map(annotation -> annotator.annotate(annotation, eventBuilder))
            .orElse(eventBuilder);
  }

  protected abstract Optional<T> getEventAnnotation(PackagedEvent.Builder eventBuilder);
}
