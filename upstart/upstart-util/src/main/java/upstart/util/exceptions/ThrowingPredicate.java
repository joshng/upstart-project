package upstart.util.exceptions;

import java.util.function.Predicate;

@FunctionalInterface
public interface ThrowingPredicate<T> extends FalliblePredicate<T, Exception> {
  static <T> Predicate<T> uncheckedPredicate(ThrowingPredicate<T> pred) {
    return pred;
  }

  @Override
  default ThrowingPredicate<T> negate() {
    return t -> !testOrThrow(t);
  }
}
