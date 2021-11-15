package upstart.util.exceptions;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;

public class UncheckedIO {
  public static byte[] captureBytes(int initialBufferSize, FallibleConsumer<OutputStream, ? extends IOException> writer) {
    return getUnchecked(() -> {
      ByteArrayOutputStream out = new ByteArrayOutputStream(initialBufferSize);
      writer.acceptOrThrow(out);
      return out.toByteArray();
    });
  }

  public static <T> T getUnchecked(FallibleSupplier<T, ? extends IOException> supplier) {
    try {
      return supplier.getOrThrow();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public static void runUnchecked(Fallible<? extends IOException> runnable) {
    try {
      runnable.runOrThrow();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public static IoRunnable runnable(IoRunnable runnable) {
    return runnable;
  }

  public static <T> IoConsumer<T> consumer(IoConsumer<T> consumer) {
    return consumer;
  }

  public static <T> IoSupplier<T> supplier(IoSupplier<T> supplier) {
    return supplier;
  }

  public static <I, O> IoFunction<I, O> function(IoFunction<I, O> function) {
    return function;
  }

  @FunctionalInterface
  public interface IoConsumer<T> extends FallibleConsumer<T, IOException> {

    @Override
    default void accept(T input) {
      runUnchecked(() -> acceptOrThrow(input));
    }
  }

  @FunctionalInterface
  public interface IoSupplier<T> extends FallibleSupplier<T, IOException> {
    default T get() {
      return getUnchecked(this);
    }
  }

  @FunctionalInterface
  public interface IoRunnable extends Fallible<IOException> {
    @Override
    default void run() {
      runUnchecked(this);
    }
  }

  @FunctionalInterface
  public interface IoFunction<I, O> extends FallibleFunction<I, O, IOException> {
    @Override
    default O apply(I input) {
      return getUnchecked(bind(input));
    }
  }
}
