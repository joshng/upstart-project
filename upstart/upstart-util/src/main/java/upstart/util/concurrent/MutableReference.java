package upstart.util.concurrent;

import com.google.common.base.Objects;
import upstart.util.context.TransientContext;

import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

public interface MutableReference<T> extends Supplier<T>, Callable<T> {
  void set(T value);

  default boolean compareAndSet(T expect, T update) {
    boolean matched = Objects.equal(get(), expect);
    if (matched) set(update);
    return matched;
  }

  default T getAndSet(T value) {
    T prev = get();
    set(value);
    return prev;
  }

  default Optional<T> getOptional() {
    return Optional.ofNullable(get());
  }

  default T computeIfAbsent(Supplier<? extends T> defaultComputer) {
    T value;
    do {
      value = get();
    } while (value == null && !compareAndSet(null, value = defaultComputer.get()));
    return value;
  }

  default T updateAndGet(UnaryOperator<T> transformer) {
    T currentValue;
    T newValue;
    do {
      currentValue = get();
      newValue = transformer.apply(currentValue);
    } while (!compareAndSet(currentValue, newValue));
    return newValue;
  }


  @Override
  default T call() {
    return get();
  }

  default TransientContext contextWithValue(T value) {
    return () -> {
      T existingValue = getAndSet(value);
      return () -> set(existingValue);
    };
  }

  default TransientContext contextWithUpdatedValue(UnaryOperator<T> updater) {
    return () -> {
      T existingValue;
      T newValue;
      do {
        existingValue = get();
        newValue = updater.apply(existingValue);
      } while (!compareAndSet(existingValue, newValue));
      T reset = existingValue;
      return () -> set(reset);
    };
  }
}

