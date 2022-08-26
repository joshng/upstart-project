package io.upstartproject.avrocodec.upstart;

import com.google.inject.BindingAnnotation;
import upstart.util.annotations.Identifier;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.CONSTRUCTOR, ElementType.FIELD, ElementType.PARAMETER})
@BindingAnnotation
@Identifier
public @interface DataStore {
  String value();

  class Factory {
    public static DataStore dataStore(String value) {
      return ImmutableDataStore.of(value);
    }
  }
}
