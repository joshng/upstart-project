package upstart.util.collect;

import com.google.common.collect.ImmutableMap;
import upstart.util.concurrent.SimpleReference;

import java.util.EnumMap;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;

public interface MoreCollectors {

  static <T, K> Collector<T, ?, ImmutableMap<K, T>> toImmutableIndexMap(Function<? super T, ? extends K> keyExtractor) {
    return ImmutableMap.toImmutableMap(keyExtractor, Function.identity());
  }

  static <I, O> Collector<I, ?, O> foldLeft(O identity, BiFunction<O, I, O> accumulator) {
    return Collector.of(
            () -> new SimpleReference<>(identity),
            (ref, input) -> ref.mergeAndGet(input, accumulator),
            (left, right) -> { throw new UnsupportedOperationException("foldLeft does not support parallelism/merging, use Stream#reduce instead"); },
            SimpleReference::get
    );
  }

  static <T> Collector<T, ?, Optional<T>> findLast() {
    return Collector.of(
            () -> new SimpleReference<T>(),
            SimpleReference::set,
            (left, right) -> { throw new UnsupportedOperationException("findLast does not support parallelism/merging, use Stream#reduce instead"); },
            SimpleReference::getOptional
    );
  }

  static <K extends Enum<K>, V> Supplier<EnumMap<K, V>> enumMapSupplier(Class<K> enumClass) {
    return () -> new EnumMap<>(enumClass);
  }
}
