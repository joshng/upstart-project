package upstart.util.context;

import upstart.util.concurrent.MutableReference;
import upstart.util.concurrent.Promise;

import java.util.concurrent.CompletableFuture;
import java.util.function.BinaryOperator;

public abstract class AsyncLocal<T> implements MutableReference<T> {
  private final String name;

  protected AsyncLocal(String name) {
    this.name = name;
  }

  public static <T> AsyncLocal<T> newAsyncLocal(String name) {
    return new AsyncLocal<>(name) {
      public T merge(T a, T b) {
        return b;
      }
    };
  }
  public static <T> AsyncLocal<T> newAsyncLocal(String name, BinaryOperator<T> mergeStrategy) {
    return new AsyncLocal<>(name) {
      @Override
      public T merge(T a, T b) {
        return mergeStrategy.apply(a, b);
      }
    };
  }

  public static <T> AsyncLocal<T> newAsyncLocal(String name, T initialValue, BinaryOperator<T> mergeStrategy) {
    return new AsyncLocal<>(name) {
      @Override
      public T merge(T a, T b) {
        return mergeStrategy.apply(a, b);
      }

      @Override
      protected T initialValue() {
        return initialValue;
      }
    };
  }

  protected T initialValue() {
    return null;
  }

  protected abstract T merge(T a, T b);
    @Override
  public void set(T value) {
    AsyncContext.putCurrentValue(this, value);
  }

  @Override
  public T get() {
    return AsyncContext.getCurrentValue(this);
  }

  public void remove() {
    AsyncContext.removeCurrentValue(this);
  }

  public CompletableFuture<T> getFromCompletion(Promise<?> future) {
    return future.completionContext().thenApply(ctx -> ctx.getOrNull(this));
  }

  @Override
  public String toString() {
    return "AsyncLocal(" + name + ')';
  }
}
