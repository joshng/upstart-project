package io.upstartproject.avrocodec.upstart;

import com.google.inject.Key;
import io.upstartproject.avrocodec.AvroDecoder;
import io.upstartproject.avrocodec.AvroTaxonomy;
import io.upstartproject.avrocodec.EnvelopeDecoder;
import io.upstartproject.avrocodec.SchemaRegistry;
import upstart.config.UpstartModule;
import upstart.guice.AnnotationKeyedPrivateModule;
import upstart.guice.PrivateBinding;
import upstart.managedservices.ServiceLifecycle;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.lang.annotation.Annotation;

public class AvroTaxonomyModule extends UpstartModule {
  private final Annotation annotation;

  public AvroTaxonomyModule(Annotation annotation) {
    super(annotation);
    this.annotation = annotation;
  }

  @Override
  protected void configure() {
    install(new AnnotationKeyedPrivateModule(
                    annotation,
                    AvroTaxonomy.class,
                    AvroDecoder.class,
                    EnvelopeDecoder.class
            ) {
              @Override
              protected void configurePrivateScope() {
                bindPrivateBindingToAnnotatedKey(SchemaRegistry.class);
                bind(AvroTaxonomy.class).to(UpstartAvroTaxonomy.class);
              }
            }
    );
    serviceManager().manage(Key.get(AvroTaxonomy.class, annotation), ServiceLifecycle.Phase.Infrastructure);
  }

  @Singleton
  private static class UpstartAvroTaxonomy extends AvroTaxonomy {
    private final Annotation annotation;

    @Inject
    public UpstartAvroTaxonomy(
            @PrivateBinding SchemaRegistry schemaRegistry,
            @PrivateBinding Annotation annotation
    ) {
      super(schemaRegistry);
      this.annotation = annotation;
    }

    @Override
    public String serviceName() {
      return "AvroTaxonomy(" + annotation.toString() + ')';
    }
  }
}
