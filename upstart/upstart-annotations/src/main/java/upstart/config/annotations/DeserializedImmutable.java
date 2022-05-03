package upstart.config.annotations;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import org.immutables.annotate.InjectAnnotation;
import org.immutables.value.Value;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Annotation for structures contained within {@link ConfigPath}-bound values.<p/>
 *
 * Use of this annotation should only be necessary if the name of the concrete type to be deserialized
 * differs from the default pattern applied by {@link Value.Style#typeImmutable} (ie, {@code "Immutable*"})
 */
@Target({TYPE, METHOD})
@Retention(RUNTIME)
@JsonDeserialize
@AlternativeImmutableAnnotation(markerAnnotation = DeserializedImmutable.class)
//@InjectAnnotation(ifPresent = true, type = JsonTypeName.class, target = InjectAnnotation.Where.IMMUTABLE_TYPE)
//@InjectAnnotation(ifPresent = true, type = JsonNaming.class, target = InjectAnnotation.Where.IMMUTABLE_TYPE)
public @interface DeserializedImmutable {
  Class<?> deserializeAs() default Void.class;
}
