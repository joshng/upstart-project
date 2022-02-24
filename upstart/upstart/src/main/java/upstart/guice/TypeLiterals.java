package upstart.guice;

import com.google.inject.TypeLiteral;
import com.google.inject.util.Types;

import java.lang.reflect.Type;

public class TypeLiterals {
  @SuppressWarnings("unchecked")
  public static <T> Class<T> getRawType(TypeLiteral<? extends T> typeLiteral) {
    return (Class<T>) typeLiteral.getRawType();
  }

  @SuppressWarnings("unchecked")
  public static <T> TypeLiteral<T> get(Type type) {
    return (TypeLiteral<T>) TypeLiteral.get(type);
  }

  public static <T> TypeLiteral<T> getParameterized(Type containingType, Type... parameterTypes) {
    return get(Types.newParameterizedType(containingType, parameterTypes));
  }
  public static <T> TypeLiteral<T> getParameterizedWithOwner(Type owner, Type containingType, Type... parameterTypes) {
    return get(Types.newParameterizedTypeWithOwner(owner, containingType, parameterTypes));
  }
}
