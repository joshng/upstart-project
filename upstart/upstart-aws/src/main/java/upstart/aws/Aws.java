package upstart.aws;

import com.google.inject.BindingAnnotation;
import org.immutables.value.Value;
import upstart.util.annotations.Tuple;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.PARAMETER, ElementType.METHOD, ElementType.FIELD})
@Value.Immutable
@Tuple
@BindingAnnotation
public @interface Aws {
  Service value();

  enum Service {
    Defaults,
    S3,
    DynamoDB;

    public final String configPath = "upstart.aws." + toString().toLowerCase();
    public final Aws annotation = ImmutableAws.of(this);
  }
}
