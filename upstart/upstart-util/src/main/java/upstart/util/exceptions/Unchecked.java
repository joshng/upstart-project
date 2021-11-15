package upstart.util.exceptions;

import com.google.common.base.Throwables;

public class Unchecked {
  public static <T, E extends Exception> FallibleSupplier<T, E> supplier(FallibleSupplier<T, E> supplier) {
    return supplier;
  }

  public static <T> T getUnchecked(FallibleSupplier<T, ?> supplier) {
    try {
      return supplier.getOrThrow();
    } catch (Exception e) {
      Throwables.throwIfUnchecked(e);
      throw new RuntimeException(e);
    }
  }

  public static <I> void acceptUnchecked(I input, FallibleConsumer<I, ?> supplier) {
    try {
      supplier.acceptOrThrow(input);
    } catch (Exception e) {
      Throwables.throwIfUnchecked(e);
      throw new RuntimeException(e);
    }
  }


  public static <I, O> O applyUnchecked(I input, FallibleFunction<I, O, ?> f) {
    try {
      return f.applyOrThrow(input);
    } catch (Exception e) {
      Throwables.throwIfUnchecked(e);
      throw new RuntimeException(e);
    }
  }

  public static void runUnchecked(Fallible<?> runnable) {
    try {
      runnable.runOrThrow();
    } catch (Exception e) {
      Throwables.throwIfUnchecked(e);
      throw new RuntimeException(e);
    }
  }
}
