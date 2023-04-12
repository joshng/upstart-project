package upstart.telemetry;

import com.google.inject.Key;
import io.upstartproject.avro.event.GitContextAnnotation;
import io.upstartproject.avrocodec.PackableRecord;
import io.upstartproject.avrocodec.events.PackagedEvent;
import io.upstartproject.avrocodec.upstart.AvroPacker;
import io.upstartproject.avrocodec.upstart.AvroPublicationModule;
import upstart.config.GitContext;
import upstart.config.UpstartModule;
import upstart.guice.AnnotationKeyedPrivateModule;

import javax.inject.Inject;
import java.util.function.Supplier;

public class GitContextAnnotator implements PackagedEvent.Decorator {
  private final Supplier<PackableRecord<GitContextAnnotation>> annotation;

  @Inject
  public GitContextAnnotator(AvroPacker<GitContextAnnotation> annotationPacker, GitContext context) {
    annotation = annotationPacker.whenRunning(packer -> packer.makePackable(new GitContextAnnotation(
            context.branch(),
            context.commit(),
            context.timestamp().toInstant(),
            context.dirty()
    )));
  }

  @Override
  public PackagedEvent.Builder decorate(PackagedEvent.Builder eventBuilder) {
    return eventBuilder.addAnnotations(annotation.get());
  }

  public static class Module extends UpstartModule {
    @Override
    protected void configure() {
      install(new EventLogModule());
      install(new GitContext.GitContextModule());
      EventLogModule.bindGlobalDecorator(binder()).to(Key.get(GitContextAnnotator.class, EventLogModule.TELEMETRY_DATA_STORE));
      install(new AnnotationKeyedPrivateModule(EventLogModule.TELEMETRY_DATA_STORE, GitContextAnnotator.class) {
        @Override
        protected void configurePrivateScope() {
          bindPrivateBindingToAnnotatedKey(AvroPublicationModule.AvroPublicationService.class);
        }
      });
    }
  }
}
