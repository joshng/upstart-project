package upstart.telemetry;


import com.google.inject.Binder;
import com.google.inject.binder.LinkedBindingBuilder;
import com.google.inject.multibindings.Multibinder;
import io.upstartproject.avro.MessageEnvelope;
import io.upstartproject.avrocodec.events.EventLog;
import io.upstartproject.avrocodec.events.EventPublisher;
import io.upstartproject.avrocodec.events.PackagedEvent;
import io.upstartproject.avrocodec.events.PackagedEventSink;
import io.upstartproject.avrocodec.upstart.AvroPublicationModule;
import io.upstartproject.avrocodec.upstart.DataStore;
import upstart.config.UpstartModule;

import java.lang.annotation.Annotation;

public class EventLogModule extends UpstartModule {
  public static final EventLogModule INSTANCE = new EventLogModule();

  public static final String TELEMETRY = "TELEMETRY";
  public static final DataStore TELEMETRY_DATA_STORE = DataStore.Factory.dataStore(TELEMETRY);

  public static LinkedBindingBuilder<PackagedEventSink> bindEventSink(Binder binder) {
    return Multibinder.newSetBinder(binder, PackagedEventSink.class).addBinding();
  }

  public static LinkedBindingBuilder<PackagedEvent.Decorator> bindGlobalDecorator(Binder binder) {
    return decoratorMultibinder(binder).addBinding();
  }

  public static Multibinder<PackagedEvent.Decorator> decoratorMultibinder(Binder binder) {
    binder.install(INSTANCE);
    return Multibinder.newSetBinder(binder, PackagedEvent.Decorator.class);
  }

  @Override
  protected void configure() {
    install(new MessageEnvelopeModule());
    // ensure multibinder is defined
    decoratorMultibinder(binder());

    bindEventSink(binder()).to(Slf4jEventSink.class);
    bind(EventLog.class).to(EventPublisher.class);

    Binder binder = binder();
    publishMessageEnvelopeSchema(binder);
  }

  public static void publishMessageEnvelopeSchema(Binder binder) {
    publishMessageEnvelopeSchema(binder, TELEMETRY_DATA_STORE);
  }

  public static void publishMessageEnvelopeSchema(Binder binder, Annotation taxonomyAnnotation) {
    AvroPublicationModule.publishAvroClasses(binder, taxonomyAnnotation, MessageEnvelope.class);
  }
}
