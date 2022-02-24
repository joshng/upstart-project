package upstart.config.annotations;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.immutables.annotate.InjectAnnotation;
import org.immutables.value.Value;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Declares the root-path for values to be populated from the {@link UpstartConfig} into the annotated structure.
 * @see {@link UpstartModule#bindConfig}
 */
@Target({TYPE, METHOD})
@Retention(RUNTIME)
@JsonDeserialize
//@InjectAnnotation(ifPresent = true, type = JsonTypeInfo.class, target = InjectAnnotation.Where.IMMUTABLE_TYPE)
public @interface ConfigPath {
  /**
   * the {@link com.typesafe.config.Config} path to the configuration value.
   */
  String value();
}
