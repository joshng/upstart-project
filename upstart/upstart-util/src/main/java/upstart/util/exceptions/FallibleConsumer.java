package upstart.util.exceptions;

import java.util.function.Consumer;
import java.util.function.Function;

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

  default <I> FallibleConsumer<I, E> compose(Function<? super I, ? extends T> f) {
    return input -> accept(f.apply(input));
  }

  default <I, E2 extends E> FallibleConsumer<I, E> compose(FallibleFunction<? super I, ? extends T, E2> f) {
    return input -> accept(f.apply(input));
  }

  default FallibleFunction<T, Void, E> asVoidFunction() {
    return input -> {
      acceptOrThrow(input);
      return null;
    };
  }
}
