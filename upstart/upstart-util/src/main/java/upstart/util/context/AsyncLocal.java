package upstart.util.context;

import upstart.util.concurrent.MutableReference;
import upstart.util.concurrent.OptionalPromise;
import upstart.util.concurrent.Promise;

import java.util.Objects;
import java.util.function.BinaryOperator;

import static com.google.common.base.Preconditions.checkState;

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
  public static <T> AsyncLocal<T> newImmutableAsyncLocal(String name) {
    return new AsyncLocal<>(name) {
      public T merge(T a, T b) {
        checkState(Objects.equals(a, b), "Conflicting values for immutable AsyncLocal: %s != %s", a, b);
        return a;
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
    if (value != null) {
      AsyncLocalContextManager.putCurrentValue(this, value);
    } else {
      AsyncLocalContextManager.removeCurrentValue(this);
    }
  }

  @Override
  public T get() {
    return AsyncLocalContextManager.getCurrentValue(this);
  }

  public void remove() {
    AsyncLocalContextManager.removeCurrentValue(this);
  }

  public OptionalPromise<T> getFromCompletion(Promise<?> future) {
      // TODO: could extract via the AsyncLocalContextManager's state directly
    // TODO: using the snapshot-context could expose unwanted local context
    return future.completionContext()
            .thenApplyOptional(snapshot -> AsyncLocalContextManager.getFromContext(snapshot, this));
  }

  @Override
  public String toString() {
    return "AsyncLocal(" + name + ')';
  }
}
