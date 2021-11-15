package upstart.util.exceptions;

import com.google.common.base.Throwables;

import java.util.function.BiFunction;

@FunctionalInterface
public interface ThrowingBiFunction<T, U, R> extends BiFunction<T, U, R> {
  R applyOrThrow(T input1, U input2) throws Exception;

  @Override
  default R apply(T input1, U input2) {
    try {
      return applyOrThrow(input1, input2);
    } catch (Exception e) {
      Throwables.throwIfUnchecked(e);
      throw new RuntimeException(e);
    }
  }

  static <T, U, R> ThrowingBiFunction<T, U, R> throwingBiFunction(ThrowingBiFunction<T, U, R> f) {
    return f;
  }
}
