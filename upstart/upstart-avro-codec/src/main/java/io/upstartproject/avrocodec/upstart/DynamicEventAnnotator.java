package io.upstartproject.avrocodec.upstart;

import com.google.inject.TypeLiteral;
import io.upstartproject.avrocodec.events.PackagedEvent;
import org.apache.avro.specific.SpecificRecordBase;

import javax.inject.Inject;

public class DynamicEventAnnotator<T extends SpecificRecordBase> extends AvroPacker<T> {

  @Inject
  public DynamicEventAnnotator(TypeLiteral<T> eventType, @DataStore("TELEMETRY") AvroPublicationModule.AvroPublicationService codecService) {
    super(eventType, codecService);
  }

  public PackagedEvent.Builder annotate(T annotation, PackagedEvent.Builder eventBuilder) {
    return eventBuilder.addAnnotations(makePackable(annotation));
  }
}
