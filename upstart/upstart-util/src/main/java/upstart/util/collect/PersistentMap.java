/*
 * Copyright (c) 2008 Harold Cooper. All rights reserved.
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */

package upstart.util.collect;

import com.google.common.base.Equivalence;
import org.pcollections.ConsPStack;
import org.pcollections.IntTreePMap;
import org.pcollections.PMap;
import org.pcollections.PSequence;
import upstart.util.functions.TriFunction;

import javax.annotation.concurrent.NotThreadSafe;
import java.io.Serial;
import java.io.Serializable;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;
import java.util.stream.Stream;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * A persistent map from non-null keys to non-null values.
 *
 * <p>This map uses a given integer map to map hashcodes to lists of elements with the same
 * hashcode. Thus if all elements have the same hashcode, performance is reduced to that of an
 * association list.
 *
 * <p>This implementation is thread-safe (assuming Java's AbstractMap and AbstractSet are
 * thread-safe), although its iterators may not be.
 *
 * @param <K>
 * @param <V>
 * @author harold
 */
public final class PersistentMap<K, V> extends AbstractMap<K, V> implements PMap<K, V>, Serializable {

  @Serial private static final long serialVersionUID = 1L;
  public static final Equivalence<Object> DEFAULT_VALUE_EQUIVALENCE = Equivalence.equals();
  @SuppressWarnings({"unchecked", "rawtypes"})
  private static final PersistentMap EMPTY = new PersistentMap(IntTreePMap.empty(), 0, DEFAULT_VALUE_EQUIVALENCE, null);
  @SuppressWarnings("rawtypes") public static final TriFunction THROWING_MERGE_FUNCTION = (k, a, b) -> {
    throw new IllegalArgumentException("Multiple entries with the same key: " + k + "=" + a + " and " + k + "=" + b);
  };

  //// STATIC FACTORY METHODS ////

  @SuppressWarnings("unchecked")
  public static <K, V> PersistentMap<K, V> empty() {
    return (PersistentMap<K, V>) EMPTY;
  }

  public static <K, V> PersistentMap<K, V> empty(Equivalence<? super V> valueEquivalence) {
    return valueEquivalence == DEFAULT_VALUE_EQUIVALENCE
            ? empty()
            : new PersistentMap<>(IntTreePMap.empty(), 0, valueEquivalence, null);
  }

  public static <K, V> Builder<K, V> builder() {
    return builder(throwingMergeFunction());
  }

  public static <K, V> Builder<K, V> builder(TriFunction<? super K, ? super V, ? super V, ? extends V> mergeFunction) {
    return builder(empty(), mergeFunction);
  }

  public static <K, V> Builder<K, V> builder(PersistentMap<? extends K, ? extends V> start) {
    return builder(start, throwingMergeFunction());
  }

  @SuppressWarnings("unchecked")
  public static <K, V> Builder<K, V> builder(
          PersistentMap<? extends K, ? extends V> start,
          TriFunction<? super K, ? super V, ? super V, ? extends V> mergeFunction
  ) {
    return new Builder<>((PersistentMap<K, V>) start, mergeFunction);
  }

  //// PRIVATE CONSTRUCTORS ////
  private final PMap<Integer, PSequence<Entry<K, V>>> intMap;
  private final int size;
  private final Equivalence<? super V> valueEquivalence;
  private final PersistentMap<K, V> emptyInstance;

  // not externally instantiable (or subclassable):
  private PersistentMap(
          final PMap<Integer, PSequence<Entry<K, V>>> intMap,
          final int size,
          Equivalence<? super V> valueEquivalence,
          PersistentMap<K, V> emptyInstance
  ) {
    this.intMap = intMap;
    this.size = size;
    this.valueEquivalence = valueEquivalence;
    this.emptyInstance = emptyInstance != null ? emptyInstance : this;
  }

  //// REQUIRED METHODS FROM AbstractMap ////
  // this cache variable is thread-safe since assignment in Java is atomic:
  private transient Set<Entry<K, V>> entrySet = null;

  @Override
  public Set<Entry<K, V>> entrySet() {
    if (entrySet == null)
      entrySet =
              new AbstractSet<>() {
                // REQUIRED METHODS OF AbstractSet //
                @Override
                public int size() {
                  return size;
                }

                @Override
                public Iterator<Entry<K, V>> iterator() {
                  return new SequenceIterator<>(intMap.values().iterator());
                }

                // OVERRIDDEN METHODS OF AbstractSet //
                @Override
                public boolean contains(final Object e) {
                  if (e instanceof Entry entry) {
                    V value = get(entry.getKey());
                    //noinspection unchecked
                    return value != null && valueEquivalence.equivalent(value, (V) entry.getValue());
                  } else {
                    return false;
                  }
                }
              };
    return entrySet;
  }

  //// OVERRIDDEN METHODS FROM AbstractMap ////
  @Override
  public int size() {
    return size;
  }

  @Override
  public boolean containsKey(final Object key) {
    return keyIndexIn(getEntries(key.hashCode()), key) != -1;
  }

  @SuppressWarnings("unchecked")
  @Override
  public boolean containsValue(Object value) {
    return values().stream().anyMatch(valueEquivalence.equivalentTo((V) value));
  }

  @Override
  public V get(final Object key) {
    PSequence<Entry<K, V>> entries = getEntries(key.hashCode());
    for (Entry<K, V> entry : entries) if (entry.getKey().equals(key)) return entry.getValue();
    return null;
  }

  //// IMPLEMENTED METHODS OF PMap////
  public PersistentMap<K, V> plusAll(final Map<? extends K, ? extends V> map) {
    if (map.isEmpty() || map == this) return this;
    PersistentMap<K, V> result = this;
    for (Entry<? extends K, ? extends V> entry : map.entrySet())
      result = result.plus(entry);
    return result;
  }

  public PersistentMap<K, V> minusAll(final Collection<?> keys) {
    PersistentMap<K, V> result = this;
    for (Object key : keys) result = result.minus(key);
    return result;
  }

  public PersistentMap<K, V> plus(final K key, final V value) {
    return plus(new SimpleImmutableEntry<>(key, value));
  }

  public PersistentMap<K, V> plus(Entry<? extends K, ? extends V> newEntry) {
    K key = newEntry.getKey();
    int hashCode = key.hashCode();
    PSequence<Entry<K, V>> entries = getEntries(hashCode);
    boolean replaced = false;
    int i = 0;
    for (Entry<K, V> entry : entries) {
      if (entry.getKey().equals(key)) {
        if (valueEquivalence.equivalent(entry.getValue(), newEntry.getValue())) return this;
        replaced = true;
        break;
      }
      i++;
    }

    int newSize;
    if (replaced) {
      entries = entries.minus(i);
      newSize = size;
    } else {
      newSize = size + 1;
    }
    return mutation(intMap.plus(hashCode, entries.plus(ensureImmutable(newEntry))), newSize);
  }

  @SuppressWarnings("unchecked")
  private static <K, V> SimpleImmutableEntry<K, V> ensureImmutable(Entry<? extends K, ? extends V> newEntry) {
    return newEntry instanceof SimpleImmutableEntry se
            ? se
            : new SimpleImmutableEntry<>(newEntry);
  }

  public PersistentMap<K, V> plusIfAbsent(K key, V value) {
    return plusComputeIfAbsent(key, k -> value);
  }

  public PersistentMap<K, V> plusComputeIfAbsent(final K key, Function<? super K, ? extends V> value) {
    int hashCode = key.hashCode();
    PSequence<Entry<K, V>> entries = getEntries(hashCode);
    if (keyIndexIn(entries, key) != -1) return this;
    return mutation(intMap.plus(hashCode, entries.plus(new SimpleImmutableEntry<>(key, value.apply(key)))), size + 1);
  }

  public PersistentMap<K, V> plusComputeIfAbsent(K key, Supplier<? extends V> value) {
    return plusComputeIfAbsent(key, k -> value.get());
  }

  public PersistentMap<K, V> plusMerge(K key, V value, TriFunction<? super K, ? super V, ? super V, ? extends V> merge) {
    return plusMerge(new SimpleImmutableEntry<>(key, value), merge);
  }

  public PersistentMap<K, V> plusMerge(
          Entry<? extends K, ? extends V> newEntry,
          TriFunction<? super K, ? super V, ? super V, ? extends V> merge
  ) {
    K key = newEntry.getKey();
    V value = newEntry.getValue();
    int hashCode = key.hashCode();
    PSequence<Entry<K, V>> entries = getEntries(hashCode);
    int i = 0;
    for (Entry<K, V> entry : entries) {
      if (entry.getKey().equals(key)) {
        V oldValue = entry.getValue();
        V newValue = merge.apply(key, oldValue, value);
        if (valueEquivalence.equivalent(oldValue, newValue)) return this;
        entries = entries.minus(i);
        return newValue == null
                ? mutation(intMap.plus(hashCode, entries), size - 1)
                : mutation(intMap.plus(hashCode, entries.plus(new SimpleImmutableEntry<>(key, newValue))), size);
      }
      i++;
    }
    return mutation(intMap.plus(hashCode, entries.plus(ensureImmutable(newEntry))), size + 1);
  }

  public PersistentMap<K, V> plusMergeAll(
          Map<? extends K, ? extends V> other,
          TriFunction<? super K, ? super V, ? super V, ? extends V> merge
  ) {
    if (other.isEmpty() || other == this) return this;

    @SuppressWarnings("unchecked") var otherNarrowed = (Map<K, V>) other;
    if (isEmpty()) return copyOf(otherNarrowed, valueEquivalence);

    PersistentMap<K, V> result = this;
    for (Entry<? extends K, ? extends V> entry : other.entrySet()) {
      result = result.plusMerge(entry, merge);
    }
    return result;
  }

  public Builder<K, V> toBuilder() {
    return toBuilder(throwingMergeFunction());
  }

  public Builder<K, V> toBuilder(TriFunction<? super K, ? super V, ? super V, ? extends V> mergeFunction) {
    return new Builder<>(this, mergeFunction);
  }

  public <T> Collector<T, ?, PersistentMap<K, V>> collector(
          Function<? super T, ? extends K> keyFunction,
          Function<? super T, ? extends V> valueFunction
  ) {
    return collector(keyFunction, valueFunction, throwingMergeFunction());
  }

  public <T> Collector<T, ?, PersistentMap<K, V>> collector(
          Function<? super T, ? extends K> keyFunction,
          Function<? super T, ? extends V> valueFunction,
          TriFunction<? super K, ? super V, ? super V, ? extends V> mergeFunction
  ) {
    return toPersistentMap(keyFunction, valueFunction, this, mergeFunction);
  }

  public Collector<Map.Entry<? extends K, ? extends V>, ?, PersistentMap<K, V>> entryCollector() {
    return entriesToPersistentMap(this);
  }

  public Collector<Map.Entry<? extends K, ? extends V>, ?, PersistentMap<K, V>> entryCollector(TriFunction<? super K, ? super V, ? super V, ? extends V> mergeFunction) {
    return entriesToPersistentMap(this, mergeFunction);
  }

  public static <K, V> PersistentMap<K, V> copyOf(Map<K, V> map) {
    return copyOf(map, DEFAULT_VALUE_EQUIVALENCE);
  }

  public static <K, V> PersistentMap<K, V> copyOf(Map<K, V> map, Equivalence<? super V> valueEquivalence) {
    if (map instanceof PersistentMap<K, V> pm) {
      if (pm.valueEquivalence.equals(valueEquivalence)) {
        return pm;
      } else {
        PersistentMap<K, V> empty = empty(valueEquivalence);
        return pm.isEmpty() ? empty : new PersistentMap<>(pm.intMap, pm.size, valueEquivalence, empty);
      }
    }
    return map.entrySet().stream().collect(entriesToPersistentMap(empty(valueEquivalence)));
  }

  public static <K, V> PersistentMap<K, V> copyOf(Stream<? extends Entry<K, V>> entries, Equivalence<? super V> valueEquivalence, TriFunction<? super K, ? super V, ? super V, ? extends V> mergeFunction) {
    return entries.collect(entriesToPersistentMap(empty(valueEquivalence), mergeFunction));
  }

  public static <T, K, V> Collector<T, ?, PersistentMap<K, V>> toPersistentMap(
          Function<? super T, ? extends K> keyFunction,
          Function<? super T, ? extends V> valueFunction
  ) {
    return toPersistentMap(keyFunction, valueFunction, throwingMergeFunction());
  }

  public static <T, K, V> Collector<T, ?, PersistentMap<K, V>> toPersistentMap(
          Function<? super T, ? extends K> keyFunction,
          Function<? super T, ? extends V> valueFunction,
          PersistentMap<K, V> startingMap
  ) {
    return toPersistentMap(keyFunction, valueFunction, startingMap, throwingMergeFunction());
  }

  public static <T, K, V> Collector<T, ?, PersistentMap<K, V>> toPersistentMap(
          Function<? super T, ? extends K> keyFunction,
          Function<? super T, ? extends V> valueFunction,
          TriFunction<? super K, ? super V, ? super V, ? extends V> mergeFunction
  ) {
    return toPersistentMap(keyFunction, valueFunction, empty(), mergeFunction);
  }

  public static <T, K, V> Collector<T, ?, PersistentMap<K, V>> toPersistentMap(
          Function<? super T, ? extends K> keyFunction,
          Function<? super T, ? extends V> valueFunction,
          PersistentMap<K, V> startingMap,
          TriFunction<? super K, ? super V, ? super V, ? extends V> mergeFunction
  ) {
    checkNotNull(keyFunction, "keyFunction");
    checkNotNull(valueFunction, "valueFunction");
    return Collector.of(
            () -> startingMap.toBuilder(mergeFunction),
            (map, input) -> map.put(keyFunction.apply(input), valueFunction.apply(input)),
            Builder::merge,
            Builder::build
    );
  }

  public static <K, V> Collector<Map.Entry<? extends K, ? extends V>, ?, PersistentMap<K, V>> entriesToPersistentMap() {
    return entriesToPersistentMap(throwingMergeFunction());
  }

  public static <K, V> Collector<Map.Entry<? extends K, ? extends V>, ?, PersistentMap<K, V>> entriesToPersistentMap(PersistentMap<K, V> startingMap) {
    return entriesToPersistentMap(startingMap, throwingMergeFunction());
  }

  public static <K, V> Collector<Map.Entry<? extends K, ? extends V>, ?, PersistentMap<K, V>> entriesToPersistentMap(TriFunction<? super K, ? super V, ? super V, ? extends V> mergeFunction) {
    return entriesToPersistentMap(empty(), mergeFunction);
  }

  public static <K, V> Collector<Map.Entry<? extends K, ? extends V>, ?, PersistentMap<K, V>> entriesToPersistentMap(PersistentMap<K, V> startingMap, TriFunction<? super K, ? super V, ? super V, ? extends V> mergeFunction) {
    return Collector.of(
            () -> startingMap.toBuilder(mergeFunction),
            Builder::put,
            Builder::merge,
            Builder::build
    );
  }

  @SuppressWarnings("unchecked")
  public static <V> TriFunction<Object, Object, Object, V> throwingMergeFunction() {
    return (TriFunction<Object, Object, Object, V>) THROWING_MERGE_FUNCTION;
  }

  @Override
  public V computeIfAbsent(K key, Function<? super K, ? extends V> mappingFunction) {
    throw new UnsupportedOperationException(
            "This is the mutating form of computeIfAbsent, unsupported by PersistentMap. Use getOrDefault, or the variant of computeIfAbsent that takes a Supplier");
  }

  public PersistentMap<K, V> minus(final Object key) {
    int hashCode = key.hashCode();
    PSequence<Entry<K, V>> entries = getEntries(hashCode);
    int i = keyIndexIn(entries, key);
    if (i == -1) return this; // key not in this
    if (entries.size() == 1) { // get rid of the entire hash entry
      return mutation(intMap.minus(hashCode), size - 1);
    } else {
      // otherwise replace hash entry with new smaller one:
      return mutation(intMap.plus(hashCode, entries.minus(i)), size - 1);
    }
  }

  //// PRIVATE UTILITIES ////
  private PSequence<Entry<K, V>> getEntries(final int hash) {
    PSequence<Entry<K, V>> entries = intMap.get(hash);
    return entries == null ? ConsPStack.empty() : entries;
  }

  private PersistentMap<K, V> mutation(final PMap<Integer, PSequence<Entry<K, V>>> intMap, final int size) {
    return size > 0
            ? new PersistentMap<>(intMap, size, valueEquivalence, emptyInstance)
            : emptyInstance;
  }

  //// PRIVATE STATIC UTILITIES ////
  private static <K, V> int keyIndexIn(final PSequence<Entry<K, V>> entries, final Object key) {
    int i = 0;
    for (Entry<K, V> entry : entries) {
      if (entry.getKey().equals(key)) return i;
      i++;
    }
    return -1;
  }

  static class SequenceIterator<E> implements Iterator<E> {
    private final Iterator<PSequence<E>> i;
    private PSequence<E> seq = ConsPStack.empty();

    SequenceIterator(Iterator<PSequence<E>> i) {
      this.i = i;
    }

    public boolean hasNext() {
      return seq.size() > 0 || i.hasNext();
    }

    public E next() {
      if (seq.size() == 0) seq = i.next();
      final E result = seq.get(0);
      seq = seq.subList(1, seq.size());
      return result;
    }

    public void remove() {
      throw new UnsupportedOperationException();
    }
  }

  @NotThreadSafe
  public static class Builder<K, V> {
    private final TriFunction<? super K, ? super V, ? super V, ? extends V> mergeFunction;
    private PersistentMap<K, V> map;

    public Builder(PersistentMap<K, V> start, TriFunction<? super K, ? super V, ? super V, ? extends V> mergeFunction) {
      this.mergeFunction = mergeFunction;
      map = start;
    }

    public Builder<K, V> put(K key, V value) {
      map = map.plusMerge(key, value, mergeFunction);
      return this;
    }

    public Builder<K, V> put(Entry<? extends K, ? extends V> entry) {
      map = map.plusMerge(entry, mergeFunction);
      return this;
    }

    public Builder<K, V> putAll(Map<? extends K, ? extends V> map) {
      this.map = this.map.plusMergeAll(map, mergeFunction);
      return this;
    }

    public Builder<K, V> merge(Builder<? extends K, ? extends V> other) {
      return putAll(other.map);
    }

    public PersistentMap<K, V> build() {
      return map;
    }
  }
}
