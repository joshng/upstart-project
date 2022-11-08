package upstart.guice;

import com.google.inject.Key;
import com.google.inject.PrivateModule;
import com.google.inject.TypeLiteral;
import com.google.inject.binder.LinkedBindingBuilder;

import java.lang.reflect.Type;

public abstract class UpstartPrivateModule extends PrivateModule {
  public static Key<?> privateBindingKey(Type type) {
    return Key.get(type, PrivateBinding.class);
  }

  public static <T> Key<T> privateBindingKey(Class<T> type) {
    return Key.get(type, PrivateBinding.class);
  }

  public static <T> Key<T> privateBindingKey(TypeLiteral<T> type) {
    return Key.get(type, PrivateBinding.class);
  }

  public static <T> Key<?> privateBindingKey(Key<T> type) {
    return type.withAnnotation(PrivateBinding.class);
  }

  protected <T> LinkedBindingBuilder<T> bindPrivateBinding(Class<T> boundType) {
    return bind(UpstartPrivateModule.privateBindingKey(boundType));
  }
  protected <T> LinkedBindingBuilder<T> bindPrivateBinding(TypeLiteral<T> boundType) {
    return bind(UpstartPrivateModule.privateBindingKey(boundType));
  }
}
