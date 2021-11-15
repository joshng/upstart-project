package upstart.guice;

import com.google.inject.TypeLiteral;

public class TypeLiterals {
  @SuppressWarnings("unchecked")
  public static <T> Class<T> getRawType(TypeLiteral<? extends T> typeLiteral) {
    return (Class<T>) typeLiteral.getRawType();
  }
}
