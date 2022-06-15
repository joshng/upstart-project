package upstart.util.context;

import com.google.common.util.concurrent.MoreExecutors;
import org.pcollections.PMap;
import upstart.util.collect.PersistentMap;
import upstart.util.concurrent.ThreadLocalReference;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.function.UnaryOperator;

public class AsyncContext implements TransientContext {
  public static final AsyncContext EMPTY = new AsyncContext(PersistentMap.empty());

  private static final ThreadLocalReference<AsyncContext> THREAD_CONTEXT = new ThreadLocalReference<>() {
    @Override
    protected AsyncContext initialValue() {
      return EMPTY;
    }
  };

  private static final TransientContext ROOT_CONTEXT = THREAD_CONTEXT.contextWithValue(EMPTY);

  public static final ContextedExecutor DIRECT_CONTEXTED_EXECUTOR = new ContextedExecutor(MoreExecutors.directExecutor());

  private final PMap<AsyncLocal<?>, Object> state;

  private AsyncContext(PMap<AsyncLocal<?>, Object> state) {
    this.state = state;
  }

  public static void clear() {
    THREAD_CONTEXT.set(EMPTY);
  }

  @Override
  public State open() {
    return THREAD_CONTEXT.contextWithUpdatedValue(this::withFallback).open();
  }

  public static TransientContext emptyContext() {
    return ROOT_CONTEXT;
  }

  public static void apply(AsyncContext more) {
    if (!more.isEmpty()) update(current -> current.mergeFrom(more));
  }

  public AsyncContext withFallback(AsyncContext other) {
    return other.mergeFrom(this);
  }

  @SuppressWarnings("unchecked")
  public AsyncContext mergeFrom(AsyncContext other) {
    if (this == other || isEmpty()) return other;
    if (other.isEmpty()) return this;

    PMap<AsyncLocal<?>, Object> newState = state;
    for (Map.Entry<AsyncLocal<?>, Object> entry : other.state.entrySet()) {
      AsyncLocal<Object> handle = (AsyncLocal<Object>) entry.getKey();
      var thisValue = state.get(handle);
      var otherValue = entry.getValue();
      if (thisValue == otherValue) continue;

      var mergedValue = thisValue == null
              ? otherValue
              : handle.merge(thisValue, otherValue);
      newState = newState.plus(handle, mergedValue);
    }
    return of(newState);
  }

  public static AsyncContext current() {
    return AsyncContext.THREAD_CONTEXT.get();
  }

  public boolean isEmpty() {
    return state.isEmpty();
  }

  public static <T> T getCurrentValue(AsyncLocal<T> handle) {
    AsyncContext current = current();
    T value = current.getOrNull(handle);
    if (value == null) {
      value = handle.initialValue();
      if (value != null) THREAD_CONTEXT.set(current.plus(handle, value));
    }
    return value;
  }

  @SuppressWarnings("unchecked")
  <T> T getOrNull(AsyncLocal<T> handle) {
    return (T) state.get(handle);
  }

  public static <T> void putCurrentValue(AsyncLocal<T> handle, T value) {
    update(current -> current.plus(handle, Objects.requireNonNull(value, "value")));
  }

  public static void removeCurrentValue(AsyncLocal<?> handle) {
    update(current -> current.minus(handle));
  }

  private static void update(UnaryOperator<AsyncContext> update) {
    AsyncContext current = current();
    AsyncContext updated = update.apply(current);
    if (updated != current) THREAD_CONTEXT.set(updated);
  }

  private static AsyncContext of(PMap<AsyncLocal<?>, Object> newState) {
    return newState.isEmpty() ? EMPTY : new AsyncContext(newState);
  }

  public static ContextedExecutor directExecutor() {
    return DIRECT_CONTEXTED_EXECUTOR;
  }

  public static ContextedExecutor contextualize(Executor executor) {
    return executor instanceof ContextedExecutor ce ? ce : new ContextedExecutor(executor);
  }

  private <T> AsyncContext plus(AsyncLocal<? super T> handle, T value) {
    return of(state.plus(handle, value));
  }

  private AsyncContext minus(AsyncLocal<?> handle) {
    return of(state.minus(handle));
  }

  @Override
  public String toString() {
    return "AsyncContext[" + state + ']';
  }

  static class ContextedExecutor implements Executor {
    private final Executor underlying;

    ContextedExecutor(Executor underlying) {
      this.underlying = underlying;
    }

    @Override
    public void execute(Runnable command) {
      var current = current();
      underlying.execute(THREAD_CONTEXT.contextWithValue(current).wrapRunnable(command));
    }
  }
}
