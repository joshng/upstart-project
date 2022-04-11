package io.upstartproject.jdbi;

import com.google.inject.BindingAnnotation;
import org.immutables.value.Value;
import upstart.util.annotations.Identifier;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.PARAMETER, ElementType.FIELD, ElementType.METHOD})
@BindingAnnotation
@Value.Immutable
@Identifier
@Value.Style(typeImmutable = "*s")
public @interface Database {
  String value();
}
