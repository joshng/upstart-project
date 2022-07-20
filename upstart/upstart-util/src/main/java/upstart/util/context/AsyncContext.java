package upstart.util.context;

import com.google.common.collect.Lists;
import com.google.common.util.concurrent.MoreExecutors;
import upstart.util.collect.PairStream;
import upstart.util.collect.PersistentMap;
import upstart.util.reflect.Reflect;

import java.util.List;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.concurrent.Executor;

public class AsyncContext implements TransientContext {
  public static final AsyncContext EMPTY = new AsyncContext(PersistentMap.empty());
  private static final ContextedExecutor DIRECT_CONTEXTED_EXECUTOR = new ContextedExecutor(MoreExecutors.directExecutor());
  private final PersistentMap<AsyncContextManager<Object>, Object> managedContexts;

  private AsyncContext(PersistentMap<AsyncContextManager<Object>, Object> managedContexts) {
    this.managedContexts = managedContexts;
  }

  private static final List<AsyncContextManager<Object>> MANAGERS = Lists.newCopyOnWriteArrayList(
          Reflect.blindCast(ServiceLoader.load(AsyncContextManager.class)));

  @SuppressWarnings("unchecked")
  public static void registerContextManager(AsyncContextManager<?> manager) {
    MANAGERS.add((AsyncContextManager<Object>) manager);
  }

  public static boolean unregisterContextManager(AsyncContextManager<?> manager) {
    return MANAGERS.remove(manager);
  }

  public static void clear() {
    MANAGERS.forEach(AsyncContextManager::remove);
  }

  public static TransientContext emptyContext() {
    return AsyncContext.EMPTY;
  }

  public static AsyncContext snapshot() {
    PersistentMap<AsyncContextManager<Object>, Object> managedContexts = PersistentMap.empty();
    for (AsyncContextManager<Object> manager : MANAGERS) {
      final var map = managedContexts;
      managedContexts = manager.captureSnapshot()
              .map(value -> map.plus(manager, value))
              .orElse(map);
    }
    return AsyncContext.of(managedContexts);
  }

  public static ContextedExecutor directExecutor() {
    return DIRECT_CONTEXTED_EXECUTOR;
  }

  public static ContextedExecutor contextualize(Executor executor) {
    return executor instanceof ContextedExecutor ce ? ce : new ContextedExecutor(executor);
  }


  private static AsyncContext of(PersistentMap<AsyncContextManager<Object>, Object> managedContexts) {
    return managedContexts.isEmpty() ? EMPTY : new AsyncContext(managedContexts);
  }

  @SuppressWarnings("unchecked")
  public <T> Optional<T> getValue(Class<? extends AsyncContextManager<T>> manager) {
    return (Optional<T>) PairStream.of(managedContexts)
            .filterKeys(manager::isInstance)
            .values()
            .findFirst();
  }

  public AsyncContext mergeFrom(AsyncContext other) {
    return isEmpty()
            ? other
            : other.isEmpty()
                    ? this
                    : new AsyncContext(
                            managedContexts.plusMergeAll(other.managedContexts, AsyncContextManager::merge)
                    );
  }

  public void applyToCurrent() {
    if (!isEmpty()) managedContexts.forEach(AsyncContextManager::mergeFromSnapshot);
  }

  public void replaceCurrent() {
    for (AsyncContextManager<Object> manager : MANAGERS) {
      Optional.ofNullable(managedContexts.get(manager))
              .ifPresentOrElse(manager::restoreSnapshot, manager::remove);
    }
  }

  public boolean isEmpty() {
    return this == EMPTY;
  }

  @Override
  public State open() {
    var snapshot = snapshot();
    applyToCurrent();
    return snapshot::replaceCurrent;
  }

  public static class ContextedExecutor implements Executor {
    private final Executor underlying;

    ContextedExecutor(Executor underlying) {
      this.underlying = underlying;
    }

    @Override
    public void execute(Runnable command) {
      underlying.execute(snapshot().wrapRunnable(command));
    }
  }
}
