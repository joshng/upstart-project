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

import java.io.Serializable;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

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
 * @author harold
 * @param <K>
 * @param <V>
 */
public final class PersistentMap<K, V> extends AbstractMap<K, V> implements PMap<K, V>, Serializable {

  private static final long serialVersionUID = 1L;
  public static final Equivalence<Object> DEFAULT_VALUE_EQUIVALENCE = Equivalence.equals();
  public static final PersistentMap EMPTY = new PersistentMap<>(IntTreePMap.empty(), 0, DEFAULT_VALUE_EQUIVALENCE);

  //// STATIC FACTORY METHODS ////

  @SuppressWarnings("unchecked")
  public static <K, V> PersistentMap<K, V> empty() {
    return (PersistentMap<K, V>) EMPTY;
  }
  public static <K, V> PersistentMap<K, V> empty(Equivalence<V> valueEquivalence) {
    return valueEquivalence == DEFAULT_VALUE_EQUIVALENCE
            ? empty()
            : new PersistentMap<>(IntTreePMap.empty(), 0, valueEquivalence);
  }

  //// PRIVATE CONSTRUCTORS ////
  private final PMap<Integer, PSequence<Entry<K, V>>> intMap;
  private final int size;
  private final Equivalence<? super V> valueEquivalence;

  // not externally instantiable (or subclassable):
  private PersistentMap(final PMap<Integer, PSequence<Entry<K, V>>> intMap, final int size,
                        Equivalence<? super V> valueEquivalence
  ) {
    this.intMap = intMap;
    this.size = size;
    this.valueEquivalence = valueEquivalence;
  }

  //// REQUIRED METHODS FROM AbstractMap ////
  // this cache variable is thread-safe since assignment in Java is atomic:
  private transient Set<Entry<K, V>> entrySet = null;

  @Override
  public Set<Entry<K, V>> entrySet() {
    if (entrySet == null)
      entrySet =
          new AbstractSet<Entry<K, V>>() {
            // REQUIRED METHODS OF AbstractSet //
            @Override
            public int size() {
              return size;
            }

            @Override
            public Iterator<Entry<K, V>> iterator() {
              return new SequenceIterator<Entry<K, V>>(intMap.values().iterator());
            }
            // OVERRIDDEN METHODS OF AbstractSet //
            @Override
            public boolean contains(final Object e) {
              if (!(e instanceof Entry)) return false;
              V value = get(((Entry<?, ?>) e).getKey());
              return value != null && valueEquivalence.equivalent(value, ((Entry<K, V>) e).getValue());
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

  @Override
  public boolean containsValue(Object value) {
    return values().stream().anyMatch(valueEquivalence.equivalentTo((V)value));
  }

  @Override
  public V get(final Object key) {
    PSequence<Entry<K, V>> entries = getEntries(key.hashCode());
    for (Entry<K, V> entry : entries) if (entry.getKey().equals(key)) return entry.getValue();
    return null;
  }

  //// IMPLEMENTED METHODS OF PMap////
  public PersistentMap<K, V> plusAll(final Map<? extends K, ? extends V> map) {
    PersistentMap<K, V> result = this;
    for (Entry<? extends K, ? extends V> entry : map.entrySet())
      result = result.plus(entry.getKey(), entry.getValue());
    return result;
  }

  public PersistentMap<K, V> minusAll(final Collection<?> keys) {
    PersistentMap<K, V> result = this;
    for (Object key : keys) result = result.minus(key);
    return result;
  }

  public PersistentMap<K, V> plus(final K key, final V value) {
    PSequence<Entry<K, V>> entries = getEntries(key.hashCode());
    int size0 = entries.size();
    int i = 0;
    for (Entry<K, V> entry : entries) {
      if (entry.getKey().equals(key)) {
        if (valueEquivalence.equivalent(entry.getValue(), value)) return this;
        break;
      }
      i++;
    }
    if (i != size0) entries = entries.minus(i);
    entries = entries.plus(new SimpleImmutableEntry<K, V>(key, value));
    return new PersistentMap<K, V>(intMap.plus(key.hashCode(), entries), size - size0 + entries.size(), valueEquivalence);
  }

  public Optional<PersistentMap<K, V>> plusIfAbsent(final K key, final V value) {
    return plusIfAbsentFrom(key, () -> value);
  }

  public Optional<PersistentMap<K, V>> plusIfAbsentFrom(final K key, final Supplier<? extends V> value) {
    PSequence<Entry<K, V>> entries = getEntries(key.hashCode());
    int size0 = entries.size();
    for (Entry<K, V> entry : entries) {
      if (entry.getKey().equals(key)) {
        return Optional.empty();
      }
    }
    entries = entries.plus(new SimpleImmutableEntry<K, V>(key, value.get()));
    return Optional.of(new PersistentMap<K, V>(intMap.plus(key.hashCode(), entries), size - size0 + entries.size(), valueEquivalence));
  }

  @Override
  public V computeIfAbsent(K key, Function<? super K, ? extends V> mappingFunction) {
    throw new UnsupportedOperationException("This is the mutating form of computeIfAbsent, unsupported by PersistentMap. Use getOrDefault, or the variant of computeIfAbsent that takes a Supplier");
  }

  public ComputeIfAbsentResult<K, V> computeIfAbsent(final K key, final Supplier<? extends V> value) {
    PSequence<Entry<K, V>> entries = getEntries(key.hashCode());
    int size0 = entries.size();
    for (Entry<K, V> entry : entries) {
      if (entry.getKey().equals(key)) {
        return new ComputeIfAbsentResult<>(this, key, entry.getValue(), true);
      }
    }
    V newValue = value.get();
    entries = entries.plus(new SimpleImmutableEntry<K, V>(key, newValue));
    PersistentMap<K, V> newMap = new PersistentMap<>(
            intMap.plus(key.hashCode(), entries),
            size - size0 + entries.size(),
            valueEquivalence
    );
    return new ComputeIfAbsentResult<>(newMap, key, newValue, false);
  }

  public static class ComputeIfAbsentResult<K, V> extends SimpleImmutableEntry<K, V> {
    private final PersistentMap<K, V> map;
    private final boolean wasPresent;

    public ComputeIfAbsentResult(PersistentMap<K, V> map, K key, V value, boolean wasPresent) {
      super(key, value);
      this.map = map;
      this.wasPresent = wasPresent;
    }

    public PersistentMap<K, V> getMap() {
      return map;
    }
    public boolean wasPresent() {
      return wasPresent;
    }
  }

  public PersistentMap<K, V> minus(final Object key) {
    PSequence<Entry<K, V>> entries = getEntries(key.hashCode());
    int i = keyIndexIn(entries, key);
    if (i == -1) // key not in this
    return this;
    entries = entries.minus(i);
    if (entries.size() == 0) // get rid of the entire hash entry
    return new PersistentMap<K, V>(intMap.minus(key.hashCode()), size - 1, valueEquivalence);
    // otherwise replace hash entry with new smaller one:
    return new PersistentMap<K, V>(intMap.plus(key.hashCode(), entries), size - 1, valueEquivalence);
  }

  //// PRIVATE UTILITIES ////
  private PSequence<Entry<K, V>> getEntries(final int hash) {
    PSequence<Entry<K, V>> entries = intMap.get(hash);
    if (entries == null) return ConsPStack.empty();
    return entries;
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
}
