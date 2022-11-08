package upstart.telemetry;

import com.google.inject.Key;
import io.upstartproject.avro.event.UpstartContextAnnotation;
import io.upstartproject.avrocodec.PackableRecord;
import io.upstartproject.avrocodec.events.PackagedEvent;
import io.upstartproject.avrocodec.upstart.AvroPacker;
import io.upstartproject.avrocodec.upstart.AvroPublicationModule;
import upstart.config.UpstartContext;
import upstart.config.UpstartModule;
import upstart.guice.AnnotationKeyedPrivateModule;

import javax.inject.Inject;
import java.util.function.Supplier;

public class UpstartContextAnnotator implements PackagedEvent.Decorator {
  private final Supplier<PackableRecord<UpstartContextAnnotation>> annotation;

  @Inject
  public UpstartContextAnnotator(AvroPacker<UpstartContextAnnotation> annotationPacker, UpstartContext context) {
    annotation = annotationPacker.whenRunning(packer -> packer.makePackable(new UpstartContextAnnotation(
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
      EventLogModule.bindGlobalDecorator(binder()).to(Key.get(UpstartContextAnnotator.class, EventLogModule.TELEMETRY_DATA_STORE));
      install(new AnnotationKeyedPrivateModule(EventLogModule.TELEMETRY_DATA_STORE, UpstartContextAnnotator.class) {
        @Override
        protected void configurePrivateScope() {
          bindPrivateBindingToAnnotatedKey(AvroPublicationModule.AvroPublicationService.class);
        }
      });
    }
  }
}
