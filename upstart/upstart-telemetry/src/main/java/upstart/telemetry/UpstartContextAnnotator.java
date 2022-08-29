package upstart.telemetry;

import com.google.inject.Key;
import com.google.inject.PrivateModule;
import io.upstartproject.avro.event.UpstartContextAnnotation;
import io.upstartproject.avrocodec.PackableRecord;
import io.upstartproject.avrocodec.events.PackagedEvent;
import io.upstartproject.avrocodec.upstart.AvroPacker;
import io.upstartproject.avrocodec.upstart.AvroPublicationModule;
import io.upstartproject.avrocodec.upstart.DataStore;
import upstart.config.UpstartContext;
import upstart.config.UpstartModule;

import javax.inject.Inject;
import java.util.function.Supplier;

public class UpstartContextAnnotator implements PackagedEvent.Decorator {
  private final Supplier<PackableRecord<UpstartContextAnnotation>> annotation;

  @Inject
  public UpstartContextAnnotator(AvroPacker<UpstartContextAnnotation> annotator, UpstartContext context) {
    annotation = annotator.whenRunning(packer -> packer.makePackable(new UpstartContextAnnotation(
            context.environment(),
            context.application(),
            context.deploymentStage().toString(),
            context.owner()
    )));
  }

  @Override
  public PackagedEvent.Builder decorate(PackagedEvent.Builder eventBuilder) {
    return eventBuilder.addAnnotations(annotation.get());
  }

  public static class Module extends UpstartModule {
    @Override
    protected void configure() {
      EventLogModule.bindGlobalDecorator(binder()).to(UpstartContextAnnotator.class);
      install(new PrivateModule() {
        @Override
        protected void configure() {
          bind(AvroPublicationModule.AvroPublicationService.class).to(Key.get(AvroPublicationModule.AvroPublicationService.class, EventLogModule.TELEMETRY_DATA_STORE));
          bind(UpstartContextAnnotator.class).asEagerSingleton();
          expose(UpstartContextAnnotator.class);
        }
      });
    }
  }
}
