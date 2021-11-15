package upstart.util;

import com.google.inject.BindingAnnotation;
import org.immutables.value.Value;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
@Value.Immutable
@Identifier
@BindingAnnotation
public @interface Numbered {
  int value();
}
