package upstart.telemetry;


import com.google.inject.Binder;
import com.google.inject.Provides;
import com.google.inject.binder.LinkedBindingBuilder;
import com.google.inject.multibindings.Multibinder;
import io.upstartproject.avrocodec.events.EventLog;
import io.upstartproject.avrocodec.events.EventPublisher;
import io.upstartproject.avrocodec.events.PackagedEvent;
import io.upstartproject.avrocodec.events.PackagedEventSink;
import io.upstartproject.avrocodec.upstart.AvroEnvelopeModule;
import upstart.config.annotations.ConfigPath;
import upstart.config.UpstartModule;

import javax.inject.Singleton;

public class EventLogModule extends UpstartModule {

  public static LinkedBindingBuilder<PackagedEventSink> bindEventSink(Binder binder) {
    return Multibinder.newSetBinder(binder, PackagedEventSink.class).addBinding();
  }

  public static LinkedBindingBuilder<PackagedEvent.Decorator> bindGlobalDecorator(Binder binder) {
    return decoratorMultibinder(binder).addBinding();
  }

  public static Multibinder<PackagedEvent.Decorator> decoratorMultibinder(Binder binder) {
    return Multibinder.newSetBinder(binder, PackagedEvent.Decorator.class);
  }

  @Override
  protected void configure() {
    install(AvroEnvelopeModule.class);

    // ensure multibinder is defined
    decoratorMultibinder(binder());

    bindEventSink(binder()).to(Slf4jEventSink.class);
    bind(EventLog.class).to(EventPublisher.class);
  }
}
