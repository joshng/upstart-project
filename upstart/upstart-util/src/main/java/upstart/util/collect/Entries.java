package upstart.util.collect;

import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToDoubleBiFunction;
import java.util.function.ToDoubleFunction;
import java.util.function.ToIntBiFunction;
import java.util.function.ToIntFunction;
import java.util.function.ToLongBiFunction;
import java.util.function.ToLongFunction;

public interface Entries {

  static <K, V, O> Function<Map.Entry<K, V>, O> tupled(BiFunction<? super K, ? super V, ? extends O> f) {
    return entry -> f.apply(entry.getKey(), entry.getValue());
  }

  static <K, V, O> ToLongFunction<Map.Entry<K, V>> tupled(ToLongBiFunction<? super K, ? super V> f) {
    return entry -> f.applyAsLong(entry.getKey(), entry.getValue());
  }

  static <K, V, O> ToIntFunction<Map.Entry<K, V>> tupled(ToIntBiFunction<? super K, ? super V> f) {
    return entry -> f.applyAsInt(entry.getKey(), entry.getValue());
  }

  static <K, V, O> ToDoubleFunction<Map.Entry<K, V>> tupled(ToDoubleBiFunction<? super K, ? super V> f) {
    return entry -> f.applyAsDouble(entry.getKey(), entry.getValue());
  }

  static <K, V> Predicate<Map.Entry<K, V>> tupled(BiPredicate<? super K, ? super V> p) {
    return entry -> p.test(entry.getKey(), entry.getValue());
  }

  static <K, V> Consumer<Map.Entry<K, V>> tupled(BiConsumer<? super K, ? super V> consumer) {
    return entry -> consumer.accept(entry.getKey(), entry.getValue());
  }

  static <K, V> Predicate<Map.Entry<K, V>> keyFilter(Predicate<? super K> filter) {
    return entry -> filter.test(entry.getKey());
  }

  static <K, V> Predicate<Map.Entry<K, V>> valueFilter(Predicate<? super V> filter) {
    return entry -> filter.test(entry.getValue());
  }

  static <K, V> Function<Map.Entry<K, V>, K> getKey() {
    return Map.Entry::getKey;
  }

  static <K, V> Function<Map.Entry<K, V>, V> getValue() {
    return Map.Entry::getValue;
  }
}

