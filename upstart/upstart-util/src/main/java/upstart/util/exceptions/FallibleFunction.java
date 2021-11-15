package upstart.util.exceptions;

import java.util.Optional;
import java.util.function.Function;

@FunctionalInterface
public interface FallibleFunction<I, O, E extends Exception> extends Function<I, O> {
  static <I, O, E extends Exception> FallibleFunction<I, O, E> fallibleFunction(FallibleFunction<I, O, E> f) {
    return f;
  }

  O applyOrThrow(I input) throws E;

  @Override
  default O apply(I i) {
    return Unchecked.applyUnchecked(i, this);
  }

  static <I, O, E extends Exception> Function<I, O> uncheckedFunction(FallibleFunction<I, O, E> f) {
    return f;
  }

  default FallibleSupplier<O, E> bind(I input) {
    return () -> applyOrThrow(input);
  }

  default Function<I, Optional<O>> optionalForException() {
    return input -> bind(input).optionalForException().get();
  }
}
