package upstart.util.exceptions;

import com.google.common.base.Throwables;

import java.util.function.Predicate;

@FunctionalInterface
public interface FalliblePredicate<T, E extends Exception> extends Predicate<T> {
  boolean testOrThrow(T input) throws E;

  @Override
  default boolean test(T input) {
    try {
      return testOrThrow(input);
    } catch (Exception e) {
      Throwables.throwIfUnchecked(e);
      throw new RuntimeException(e);
    }
  }
}
