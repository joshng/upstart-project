package upstart.util.context;

import com.google.common.base.Equivalence;
import org.kohsuke.MetaInfServices;
import upstart.util.collect.PersistentMap;
import upstart.util.concurrent.ThreadLocalReference;

import javax.annotation.Nullable;
import java.util.Objects;
import java.util.Optional;
import java.util.function.UnaryOperator;

@MetaInfServices(AsyncContextManager.class)
public class AsyncLocalContextManager implements AsyncContextManager<PersistentMap<AsyncLocal<?>, Object>> {
  private static final PersistentMap<AsyncLocal<?>, Object> EMPTY_MAP = PersistentMap.empty(Equivalence.identity());

  private static final ThreadLocalReference<PersistentMap<AsyncLocal<?>, Object>> THREAD_CONTEXT = ThreadLocalReference
          .withInitial(() -> EMPTY_MAP);

  @Override
  public Optional<PersistentMap<AsyncLocal<?>, Object>> captureSnapshot() {
    return THREAD_CONTEXT.getOptional().filter(state -> !state.isEmpty());
  }

  @Override
  public void restoreSnapshot(PersistentMap<AsyncLocal<?>, Object> value) {
    THREAD_CONTEXT.set(value);
  }

  @Override
  public void remove() {
    THREAD_CONTEXT.remove();
  }

  @Override
  public void mergeApplyFromSnapshot(PersistentMap<AsyncLocal<?>, Object> value) {
    updateCurrent(state -> mergeSnapshots(state, value));
  }

  @SuppressWarnings("unchecked")
  @Override
  public PersistentMap<AsyncLocal<?>, Object> mergeSnapshots(
          PersistentMap<AsyncLocal<?>, Object> mergeTo, PersistentMap<AsyncLocal<?>, Object> mergeFrom
  ) {
    return mergeTo.plusMergeAll(
            mergeFrom,
            ((asyncLocal, a, b) -> ((AsyncLocal<Object>) asyncLocal).merge(a, b))
    );
  }

  public static <T> T getCurrentValue(AsyncLocal<T> handle) {
    PersistentMap<AsyncLocal<?>, Object> current = THREAD_CONTEXT.get();
    T value = get(handle, current);
    if (value == null) {
      value = handle.initialValue();
      if (value != null) THREAD_CONTEXT.set(current.plus(handle, value));
    }
    return value;
  }

  public static <T> Optional<T> getIfPresent(AsyncLocal<T> handle) {
    PersistentMap<AsyncLocal<?>, Object> current = THREAD_CONTEXT.get();
    return Optional.ofNullable(get(handle, current));
  }

  public static <T> void putCurrentValue(AsyncLocal<T> handle, T value) {
    updateCurrent(current -> current.plus(handle, Objects.requireNonNull(value, "value")));
  }

  public static <T> Optional<T> getFromContext(AsyncContext snapshot, AsyncLocal<T> handle) {
    return snapshot.getValue(AsyncLocalContextManager.class).map(map -> get(handle, map));
  }

  public static void removeCurrentValue(AsyncLocal<?> handle) {
    updateCurrent(current -> current.minus(handle));
  }

  @SuppressWarnings("unchecked")
  @Nullable
  private static <T> T get(AsyncLocal<T> handle, PersistentMap<AsyncLocal<?>, Object> current) {
    return (T) current.get(handle);
  }

  private static void updateCurrent(UnaryOperator<PersistentMap<AsyncLocal<?>, Object>> update) {
    PersistentMap<AsyncLocal<?>, Object> current = THREAD_CONTEXT.get();
    PersistentMap<AsyncLocal<?>, Object> updated = update.apply(current);
    if (updated != current) THREAD_CONTEXT.set(updated);
  }
}
