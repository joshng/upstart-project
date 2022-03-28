package upstart.util.collect;

import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

public interface Entries {

  static <K, V, O> Function<Map.Entry<K, V>, O> tupled(BiFunction<? super K, ? super V, ? extends O> f) {
    return entry -> f.apply(entry.getKey(), entry.getValue());
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

