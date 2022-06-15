package upstart.util.exceptions;

import upstart.util.collect.Pair;

import javax.annotation.Nullable;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;

import static com.google.common.base.Preconditions.checkArgument;

public final class Try<T> {
  private static final Try<Void> NULL = new Try<>(null, null);
  private final T value;
  private final Throwable exception;

  private Try(T value, Throwable exception) {
    this.value = value;
    this.exception = exception;
  }

  public static <T> Try<T> success(T value) {
    return value == null ? nullSuccess() : new Try<>(value, null);
  }

  public static <T> Try<T> failure(Throwable exception) {
    return new Try<>(null, exception);
  }

  public static <T> Try<T> of(@Nullable T value, @Nullable Throwable exception) {
    checkArgument(exception == null || value == null, "Cannot create a Try with both value and exception");
    return new Try<>(value, exception);
  }

  public static <T> Try<T> call(Callable<? extends T> completion) {
    try {
      return success(completion.call());
    } catch (Throwable t) {
      return failure(t);
    }
  }

  public static <T> CompletableFuture<Try<T>> capture(CompletableFuture<T> future) {
    return future.handle(Try::new);
  }

  public static <T> Callable<Try<T>> wrap(Callable<? extends T> completion) {
    return () -> Try.call(completion);
  }

  @SuppressWarnings("unchecked")
  public static <T> Try<T> nullSuccess() {
    return (Try<T>) NULL;
  }

  public boolean isSuccess() {
    return exception == null;
  }

  public boolean isFailure() {
    return exception != null;
  }

  public T get() {
    if (isSuccess()) {
      return value;
    } else {
      throw Exceptions.throwUnchecked(exception);
    }
  }

  public Throwable getException() {
    if (isFailure()) {
      return exception;
    } else {
      throw new IllegalStateException("Try succeeded");
    }
  }

  public <U> Try<U> map(Function<? super T, U> f) {
    if (isSuccess()) {
      try {
        return Try.success(f.apply(value));
      } catch (Throwable t) {
        return Try.failure(t);
      }
    } else {
      //noinspection unchecked
      return (Try<U>) this;
    }
  }

  public <U> Try<U> flatMap(Function<? super T, Try<U>> f) {
    if (isSuccess()) {
      try {
        return f.apply(value);
      } catch (Throwable t) {
        return Try.failure(t);
      }
    } else {
      //noinspection unchecked
      return (Try<U>) this;
    }
  }

  public Try<T> recover(Function<? super Throwable, ? extends T> f) {
    if (isFailure()) {
      try {
        return Try.success(f.apply(exception));
      } catch (Throwable t) {
        return Try.failure(t);
      }
    } else {
      return this;
    }
  }

  @SuppressWarnings("unchecked")
  public Try<T> recoverWith(Function<? super Throwable, Try<? extends T>> f) {
    if (isFailure()) {
      try {
        return (Try<T>) f.apply(exception);
      } catch (Throwable t) {
        return Try.failure(t);
      }
    } else {
      return this;
    }
  }

  public <U> Try<U> handle(BiFunction<? super T, ? super Throwable, ? extends U> handler) {
    try {
      return Try.success(handler.apply(value, exception));
    } catch (Throwable t) {
      return Try.failure(t);
    }
  }

  public void accept(BiConsumer<? super T, ? super Throwable> consumer) {
    consumer.accept(value, exception);
  }

  public Try<T> filter(Predicate<? super T> p) {
    return filter("Predicate failed", p);
  }

  public Try<T> filterNot(Predicate<? super T> p) {
    return filter(p.negate());
  }

  public Try<T> filter(String message, Predicate<? super T> p) {
    if (isSuccess()) {
      try {
        if (p.test(value)) {
          return this;
        } else {
          return failure(new IllegalArgumentException(message));
        }
      } catch (Throwable t) {
        return failure(t);
      }
    } else {
      //noinspection unchecked
      return this;
    }
  }

  public Try<T> filterNot(String message, Predicate<? super T> p) {
    return filter(message, p.negate());
  }

  public <U> Try<Pair<T, U>> combine(Try<U> other) {
    if (isSuccess() && other.isSuccess()) {
      return success(Pair.of(value, other.value));
    } else {
      return failure(exception != null ? exception : other.exception);
    }
  }
}
