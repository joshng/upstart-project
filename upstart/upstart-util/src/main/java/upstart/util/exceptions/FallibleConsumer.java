package upstart.util.exceptions;

import java.util.function.Consumer;

@FunctionalInterface
public interface FallibleConsumer<T, E extends Exception> extends Consumer<T> {
  void acceptOrThrow(T input) throws E;

  @Override
  default void accept(T input) {
    Unchecked.acceptUnchecked(input, this);
  }

  default Fallible<E> bind(T input) {
    return () -> acceptOrThrow(input);
  }

  default FallibleFunction<T, Void, E> asVoidFunction() {
    return input -> {
      acceptOrThrow(input);
      return null;
    };
  }
}
