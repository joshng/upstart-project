package upstart.util.concurrent;

import javax.annotation.Nonnull;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.Supplier;

import static com.google.common.base.Preconditions.checkNotNull;

public abstract class LazyReference<T> implements Supplier<T>, Callable<T>, com.google.common.base.Supplier<T> {
  private volatile T value = null;

  public static <T> LazyReference<T> from(Supplier<T> supplier) {
    return new LazyReference<T>() {
      @Nonnull
      @Override
      protected T supplyValue() {
        return supplier.get();
      }
    };
  }

  @Nonnull
  protected abstract T supplyValue();

  @Override
  public T call() throws Exception {
    return get();
  }

  @Override
  public T get() {
    T snapshot = value;
    if (snapshot == null) {
      synchronized (this) {
        snapshot = value;
        if (snapshot == null) {
          value = snapshot = checkNotNull(supplyValue(), "supplyValue must not return null");
        }
      }
    }
    return snapshot;
  }

  public synchronized Optional<T> remove() {
    Optional<T> removed = getIfPresent();
    value = null;
    return removed;
  }

  public Optional<T> getIfPresent() {
    return Optional.ofNullable(value);
  }
}
