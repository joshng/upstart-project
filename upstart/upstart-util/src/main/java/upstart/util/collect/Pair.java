package upstart.util.collect;

import upstart.util.annotations.Tuple;
import upstart.util.functions.optics.Lens;
import org.immutables.value.Value;

import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;

@Value.Immutable
@Tuple
public interface Pair<A, B> extends Map.Entry<A, B> {

  static <A, B> Lens<Pair<A, B>, A> key() {
    return Lens.of(Map.Entry::getKey, Pair::withKey);
  }

  static <A, B> Lens<Pair<A, B>, B> value() {
    return Lens.of(Map.Entry::getValue, Pair::withValue);
  }

  static <A, B> Pair<A, B> of(A key, B value) {
    return ImmutablePair.of(key, value);
  }

  static <K, V> FactoryWithKey<K, V> factoryWithKey(K key) {
    return FactoryWithKey.of(key);
  }

  static <K, V> FactoryWithValue<K, V> factoryWithValue(V key) {
    return FactoryWithValue.of(key);
  }

  @SuppressWarnings("unchecked")
  static <A, B> Pair<A, B> of(Map.Entry<? extends A, ? extends B> entry) {
    if (entry instanceof Pair) return (Pair<A, B>) entry;
    return of(entry.getKey(), entry.getValue());
  }

  Pair<A, B> withKey(A key);

  Pair<A, B> withValue(B value);

  default <T> T map2(BiFunction<? super A, ? super B, ? extends T> visitor) {
    return visitor.apply(getKey(), getValue());
  }

  default Pair<B, A> swap() {
    return swap(this);
  }

  static <A, B> Pair<B, A> swap(Map.Entry<A, B> entry) {
    return Pair.of(entry.getValue(), entry.getKey());
  }

  @Override
  default B setValue(B value) {
    throw new UnsupportedOperationException("setValue is not supported");
  }

  interface FactoryWithKey<K, V> extends Function<V, Pair<K, V>> {
    static <K, V> FactoryWithKey<K, V> of(K key) {
      return value -> Pair.of(key, value);
    }
  }
  interface FactoryWithValue<K, V> extends Function<K, Pair<K, V>> {
    static <K, V> FactoryWithValue<K, V> of(V value) {
      return key -> Pair.of(key, value);
    }
  }
}
