package upstart.util.concurrent;

import upstart.util.context.TransientContext;

import java.util.function.Supplier;
import java.util.function.UnaryOperator;

public class ThreadLocalReference<T> extends ThreadLocal<T> implements MutableReference<T> {
  public static <T> ThreadLocalReference<T> withInitial(Supplier<? extends T> supplier) {
    return new ThreadLocalReference<>() {
      @Override
      protected T initialValue() {
        return supplier.get();
      }
    };
  }

  @Override
  public TransientContext contextWithUpdatedValue(UnaryOperator<T> updater) {
    return () -> {
      T existingValue = get();
      set(updater.apply(existingValue));
      return () -> set(existingValue);
    };
  }

  @Override
  public T computeIfAbsent(Supplier<? extends T> defaultComputer) {
    T value = get();
    if (value == null) {
      value = defaultComputer.get();
      set(value);
    }
    return value;
  }
}
