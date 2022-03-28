package upstart.util.collect;

import com.google.common.collect.ImmutableMap;

import java.util.function.Function;
import java.util.stream.Collector;

public interface MoreCollectors {

  static <T, K> Collector<T, ?, ImmutableMap<K, T>> toImmutableIndexMap(Function<? super T, ? extends K> keyExtractor) {
    return ImmutableMap.toImmutableMap(keyExtractor, Function.identity());
  }
}
