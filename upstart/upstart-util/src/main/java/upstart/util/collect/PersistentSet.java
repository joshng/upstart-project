package upstart.util.collect;

import java.util.AbstractSet;
import java.util.Iterator;
import java.util.function.Supplier;

public class PersistentSet<T> extends AbstractSet<T> {
  private static final Supplier<Boolean> TRUE_SUPPLIER = () -> true;
  public static final PersistentSet<Object> EMPTY = new PersistentSet<>(PersistentMap.empty());
  private final PersistentMap<T, Boolean> map;

  private PersistentSet(PersistentMap<T, Boolean> map) {
    this.map = map;
  }

  @SuppressWarnings("unchecked")
  public static <T> PersistentSet<T> empty() {
    return (PersistentSet<T>) EMPTY;
  }

  public static <T> PersistentSet<T> of(T... values) {
    PersistentMap<T, Boolean> map = PersistentMap.empty();
    for (T value : values) {
      map = map.plusComputeIfAbsent(value, TRUE_SUPPLIER);
    }
    return PersistentSet.<T>empty().withMap(map);
  }

  public PersistentSet<T> with(T value) {
    return withMap(map.plusComputeIfAbsent(value, TRUE_SUPPLIER));
  }

  private PersistentSet<T> withMap(PersistentMap<T, Boolean> newMap) {
    return newMap == map ? this : newMap.isEmpty() ? empty() : new PersistentSet<>(newMap);
  }

  public PersistentSet<T> without(T value) {
    return withMap(map.minus(value));
  }

  @Override
  public boolean contains(Object value) {
    return map.containsKey(value);
  }

  @Override
  public Iterator<T> iterator() {
    return map.keySet().iterator();
  }

  @Override
  public int size() {
    return map.size();
  }
}
