package upstart.util.exceptions;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

@FunctionalInterface
public interface Fallible<E extends Exception> extends Runnable, FallibleSupplier<Void, E> {
  static <E extends Exception> Fallible<E> fallible(Fallible<E> block) {
    return block;
  }

  void runOrThrow() throws E;

  @Override
  default void run() {
    Unchecked.runUnchecked(this);
  }

  @Override
  default Void getOrThrow() throws E {
    runOrThrow();
    return null;
  }

  interface ExceptionConverter<E extends Exception, U extends RuntimeException> {
    default Runnable runnable(Fallible<? extends E> fallible) {
      return () -> runOrThrow(fallible);
    }

    default <T> Supplier<T> supplier(FallibleSupplier<T, ? extends  E> supplier) {
      return () -> getOrThrow(supplier);
    }

    default <I, O> Function<I, O> function(FallibleFunction<I, O, ? extends E> function) {
      return input -> applyOrThrow(input, function);
    }

    default <T> Consumer<T> consumer(FallibleConsumer<T, ? extends E> consumer) {
      return input -> runOrThrow(consumer.bind(input));
    }

    default void runOrThrow(Fallible<? extends E> fallible) throws U {
      getOrThrow(fallible);
    }

    default <I, O> O applyOrThrow(I input, FallibleFunction<I, O, ? extends E> function) throws U {
      return getOrThrow(function.bind(input));
    }

    <T> T getOrThrow(FallibleSupplier<T, ? extends E> supplier) throws U;
  }
}
