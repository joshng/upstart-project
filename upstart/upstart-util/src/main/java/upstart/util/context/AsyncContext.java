package upstart.util.context;

import com.google.common.collect.Lists;
import com.google.common.util.concurrent.MoreExecutors;
import upstart.util.collect.PersistentMap;
import upstart.util.concurrent.ThreadLocalReference;
import upstart.util.reflect.Reflect;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.concurrent.Executor;
import java.util.function.UnaryOperator;

public class AsyncContext {
  public static final AsyncContext EMPTY = new AsyncContext(PersistentMap.empty(), PersistentMap.empty());

  private static final ThreadLocalReference<AsyncContext> THREAD_CONTEXT = new ThreadLocalReference<>() {
    @Override
    protected AsyncContext initialValue() {
      return EMPTY;
    }
  };

  private static final TransientContext ROOT_CONTEXT = THREAD_CONTEXT.contextWithValue(EMPTY);

  public static final ContextedExecutor DIRECT_CONTEXTED_EXECUTOR = new ContextedExecutor(MoreExecutors.directExecutor());

  private static List<AsyncContextManager<Object>> MANAGERS = Lists.newCopyOnWriteArrayList(
          Reflect.blindCast(ServiceLoader.load(AsyncContextManager.class)));

  private final PersistentMap<AsyncLocal<Object>, Object> state;
  private final PersistentMap<AsyncContextManager<Object>, Object> managedState;

  private AsyncContext(PersistentMap<AsyncLocal<Object>, Object> state,
                       PersistentMap<AsyncContextManager<Object>, Object> managedState
  ) {
    this.state = state;
    this.managedState = managedState;
  }

  @SuppressWarnings("unchecked")
  public static void registerContextManager(AsyncContextManager<?> manager) {
    MANAGERS.add((AsyncContextManager<Object>) manager);
  }

  public static void unregisterContextManager(AsyncContextManager<?> manager) {
    MANAGERS.remove(manager);
  }

  public static void clear() {
    MANAGERS.forEach(AsyncContextManager::remove);
    THREAD_CONTEXT.set(EMPTY);
  }

  public static TransientContext emptyContext() {
    return ROOT_CONTEXT;
  }

  public static Snapshot snapshot() {
    PersistentMap<AsyncContextManager<Object>, Object> managedContexts = PersistentMap.empty();
    for (AsyncContextManager<Object> manager : MANAGERS) {
      final var map = managedContexts;
      managedContexts = manager.captureSnapshot()
              .map(value -> map.plus(manager, value))
              .orElse(map);
    }
    return Snapshot.of(managedContexts, current());
  }

  public AsyncContext withFallback(AsyncContext other) {
    return other.mergeFrom(this);
  }

  public AsyncContext mergeFrom(AsyncContext other) {
    if (isEmpty() || this == other) return other;
    if (other.isEmpty()) return this;

    return of(
            state.plusMergeAll(other.state, AsyncLocal::merge),
            managedState.plusMergeAll(other.managedState, AsyncContextManager::merge)
    );
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
    updateCurrent(current -> current.plus(handle, Objects.requireNonNull(value, "value")));
  }

  public static void removeCurrentValue(AsyncLocal<?> handle) {
    updateCurrent(current -> current.minus(handle));
  }

  private static void updateCurrent(UnaryOperator<AsyncContext> update) {
    AsyncContext current = current();
    AsyncContext updated = update.apply(current);
    if (updated != current) THREAD_CONTEXT.set(updated);
  }

  private static AsyncContext of(
          PersistentMap<AsyncLocal<Object>, Object> newState,
          PersistentMap<AsyncContextManager<Object>, Object> managedState
  ) {
    return newState.isEmpty() && managedState.isEmpty() ? EMPTY : new AsyncContext(newState, managedState);
  }

  public static ContextedExecutor directExecutor() {
    return DIRECT_CONTEXTED_EXECUTOR;
  }

  public static ContextedExecutor contextualize(Executor executor) {
    return executor instanceof ContextedExecutor ce ? ce : new ContextedExecutor(executor);
  }

  @SuppressWarnings("unchecked")
  private <T> AsyncContext plus(AsyncLocal<? super T> handle, T value) {
    return of(state.plus((AsyncLocal<Object>) handle, value), managedState);
  }

  private AsyncContext minus(AsyncLocal<?> handle) {
    return of(state.minus(handle), managedState);
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

  public static class Snapshot implements TransientContext {
    static final Snapshot EMPTY = new Snapshot(PersistentMap.empty(), AsyncContext.EMPTY);
    private final PersistentMap<AsyncContextManager<Object>, Object> managedContexts;
    private final AsyncContext context;

    private Snapshot(PersistentMap<AsyncContextManager<Object>, Object> state, AsyncContext context) {
      managedContexts = state;
      this.context = context;
    }

    @Override
    public State open() {
      var snapshot = snapshot();
      applyToCurrent();
      return snapshot::replaceCurrent;
    }

    private static Snapshot of(
            PersistentMap<AsyncContextManager<Object>, Object> managedContexts,
            AsyncContext context
    ) {
      return managedContexts.isEmpty() && context.isEmpty() ? EMPTY : new Snapshot(managedContexts, context);
    }

    public Snapshot mergeFrom(Snapshot other) {
      return isEmpty()
              ? other
              : other.isEmpty()
                      ? this
                      : new Snapshot(
                              managedContexts.plusMergeAll(other.managedContexts, AsyncContextManager::merge),
                              context.mergeFrom(other.context)
                      );
    }

    public void applyToCurrent() {
      if (!isEmpty()) {
        updateCurrent(current -> current.mergeFrom(context));
        for (AsyncContextManager<Object> manager : MANAGERS) {
          Optional.ofNullable(managedContexts.get(manager))
                  .ifPresentOrElse(manager::restoreSnapshot, manager::remove);
        }
      }
    }

    public void replaceCurrent() {
      THREAD_CONTEXT.set(context);
      managedContexts.forEach(AsyncContextManager::restoreSnapshot);
    }

    public boolean isEmpty() {
      return this == EMPTY;
    }

    public AsyncContext asyncLocalContext() {
      return context;
    }
  }
}
