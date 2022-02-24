package upstart.guice;

import com.google.inject.BindingAnnotation;
import org.immutables.value.Value;
import upstart.util.annotations.Identifier;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
@Identifier
@BindingAnnotation
public @interface Numbered {
  int value();
}
