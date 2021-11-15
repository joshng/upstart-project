package upstart.util.exceptions;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.Supplier;

@FunctionalInterface
public interface FallibleSupplier<T, E extends Exception> extends Supplier<T>, Callable<T> {
  Logger LOG = LoggerFactory.getLogger(FallibleSupplier.class);

  static <T> Supplier<T> of(FallibleSupplier<T, ?> block) {
    return block;
  }

  T getOrThrow() throws E;

  @Override
  default T get() {
    return Unchecked.getUnchecked(this);
  }

  @Override
  default T call() throws Exception {
    return getOrThrow();
  }

  default Supplier<Optional<T>> optionalForException() {
    return () -> {
      try {
        return Optional.ofNullable(call());
      } catch (Exception e) {
        LOG.debug("Ignored error in FallibleSupplier.optionalForException", e);
        return Optional.empty();
      }
    };
  }
}
