package upstart.util.exceptions;

import java.util.function.Function;

@FunctionalInterface
public interface ThrowingFunction<I, O> extends FallibleFunction<I, O, Exception> {
  static <I, O> Function<I, O> uncheckedFunction(ThrowingFunction<I, O> f) {
    return f;
  }
}
