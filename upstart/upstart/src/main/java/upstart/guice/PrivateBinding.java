package upstart.guice;

import com.google.inject.BindingAnnotation;
import upstart.util.annotations.Tuple;
import org.immutables.value.Value;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.PARAMETER, ElementType.METHOD, ElementType.FIELD})
@BindingAnnotation
@Value.Immutable
@Tuple
public @interface PrivateBinding {
  String value() default "";
}
