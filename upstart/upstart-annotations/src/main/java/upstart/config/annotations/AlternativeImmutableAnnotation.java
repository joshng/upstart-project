package upstart.config.annotations;

import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Target(ElementType.ANNOTATION_TYPE)
@Retention(RUNTIME)
public @interface AlternativeImmutableAnnotation {
  Class<? extends Annotation> markerAnnotation();
}
