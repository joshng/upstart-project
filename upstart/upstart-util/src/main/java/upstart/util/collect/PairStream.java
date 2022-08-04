package upstart.util.collect;

import com.google.common.base.Equivalence;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.Multimaps;
import com.google.common.collect.SetMultimap;
import com.google.common.collect.Streams;
import upstart.util.concurrent.SimpleReference;
import upstart.util.reflect.Reflect;
import upstart.util.functions.TriFunction;

import java.util.AbstractMap;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Spliterator;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.BinaryOperator;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.ToDoubleFunction;
import java.util.function.ToIntFunction;
import java.util.function.ToLongFunction;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;

public class PairStream<K, V> implements Stream<Map.Entry<K, V>> {
  private final Stream<Map.Entry<K, V>> stream;

  private PairStream(Stream<Map.Entry<K, V>> stream) {
    this.stream = stream;
  }

  public static <K, V> PairStream<K, V> of(Map<K, V> map) {
    return of(map.entrySet().stream());
  }

  @SuppressWarnings("unchecked")
  public static <K, V> PairStream<K, V> of(Stream<? extends Map.Entry<K, V>> stream) {
    return new PairStream<>((Stream<Map.Entry<K,V>>) stream);
  }

  public static <K, V> PairStream<K, V> of(Iterable<? extends Map.Entry<K, V>> stream) {
    return of(Streams.stream(stream));
  }

  public static <K, V> PairStream<K, V> empty() {
    return of(Stream.empty());
  }

  public static <K, V> PairStream<K, V> zip(Stream<K> keys, Stream<V> values) {
    return of(Streams.zip(keys, values, Pair::of));
  }

  public static <K> PairStream<K, Long> zipWithIndex(Stream<K> keys) {
    return of(Streams.mapWithIndex(keys, Pair::of));
  }

  public static <K, V> PairStream<K, V> withMappedKeys(Stream<V> values, Function<? super V, K> keyMapper) {
    return of(values.map(v -> Pair.of(keyMapper.apply(v), v)));
  }

  public static <K, V> PairStream<K, V> withMappedValues(Stream<K> keys, Function<? super K, V> valueMapper) {
    return of(keys.map(k -> Pair.of(k, valueMapper.apply(k))));
  }

  public static <T> PairStream<T, T> consecutivePairs(Stream<T> stream) {
    SimpleReference<T> prev = new SimpleReference<>();
    return of(stream.sequential().map(t -> new AbstractMap.SimpleImmutableEntry<>(prev.getAndSet(t), t)))
            .skip(1);
  }

  public static <K, V> PairStream<K, V> cartesianProduct(Stream<K> keys, Supplier<Stream<V>> values) {
    return of(keys.flatMap(k -> values.get().map(Pair.factoryWithKey(k))));
  }

  public static <K, V> PairStream<K, List<V>> groupBy(Stream<V> values, Function<? super V, K> keyMapper) {
    ListMultimap<K, V> grouped = withMappedKeys(values, keyMapper).toImmutableListMultimap();
    return of(Multimaps.asMap(grouped));
  }

  public Stream<K> keys() {
    return stream.map(Entries.getKey());
  }

  public Stream<V> values() {
    return stream.map(Entries.getValue());
  }

  public PairStream<V, K> swap() {
    return of(map(Pair::swap));
  }
  // TODO: introduce MappedPairStream specialization to avoid intermediate Pair creation

  public <O> PairStream<O, V> mapKeys(Function<? super K, O> f) {
    return of(stream.map(entry -> Pair.of(f.apply(entry.getKey()), entry.getValue())));
  }

  public <O> PairStream<O, V> mapKeys(BiFunction<? super K, ? super V, O> f) {
    return of(stream.map(entry -> Pair.of(f.apply(entry.getKey(), entry.getValue()), entry.getValue())));
  }

  public <O> PairStream<K, O> mapValues(Function<? super V, O> f) {
    return of(stream.map(entry -> Pair.of(entry.getKey(), f.apply(entry.getValue()))));
  }

  public <O> PairStream<K, O> mapValues(BiFunction<? super K, ? super V, O> f) {
    return of(stream.map(entry -> Pair.of(entry.getKey(), f.apply(entry.getKey(), entry.getValue()))));
  }

  public <K2, V2> PairStream<K2, V2> mapPairs(BiFunction<? super K, ? super V, ? extends Map.Entry<K2, V2>> f) {
    return of(stream.map(Entries.tupled(f)));
  }

  public <O> PairStream<O, V> flatMapKeys(Function<? super K, ? extends Stream<O>> f) {
    return flatMapPairs((k, v) -> f.apply(k).map(Pair.factoryWithValue(v)));
  }

  public <O> PairStream<O, V> flatMapKeys(BiFunction<? super K, ? super V, ? extends Stream<O>> f) {
    return flatMapPairs((k, v) -> f.apply(k, v).map(Pair.factoryWithValue(v)));
  }

  public <O> PairStream<K, O> flatMapValues(Function<? super V, ? extends Stream<O>> f) {
    return flatMapPairs((k, v) -> f.apply(v).map(Pair.factoryWithKey(k)));
  }

  public <O> PairStream<K, O> flatMapValues(BiFunction<? super K, ? super V, ? extends Stream<O>> f) {
    return flatMapPairs((k, v) -> f.apply(k, v).map(Pair.factoryWithKey(k)));
  }

  public <K2, V2> PairStream<K2, V2> flatMapPairs(BiFunction<? super K, ? super V, ? extends Stream<? extends Map.Entry<K2, V2>>> f) {
    return of(stream.flatMap(Entries.tupled(f)));
  }

  public <O> Stream<O> flatMap(BiFunction<? super K, ? super V, Stream<O>> f) {
    return flatMap(Entries.tupled(f));
  }

  public <O> Stream<O> map(BiFunction<? super K, ? super V, O> f) {
    return map(Entries.tupled(f));
  }

  public PairStream<K, V> filterKeys(Predicate<? super K> p) {
    return of(stream.filter(Entries.keyFilter(p)));
  }

  public <K2> PairStream<K2, V> filterKeys(Class<? super K2> keyClass) {
    return Reflect.blindCast(of(stream.filter(entry -> keyClass.isInstance(entry.getKey()))));
  }

  public PairStream<K, V> filterValues(Predicate<? super V> p) {
    return of(stream.filter(Entries.valueFilter(p)));
  }

  public <V2> PairStream<K, V2> filterValues(Class<? super V2> valueClass) {
    return Reflect.blindCast(of(stream.filter(entry -> valueClass.isInstance(entry.getValue()))));
  }

  public PairStream<K, V> filter(BiPredicate<? super K, ? super V> p) {
    return of(stream.filter(Entries.tupled(p)));
  }

  public PairStream<K, V> append(K key, V value) {
    return append(Pair.of(key, value));
  }

  public PairStream<K, V> append(Map.Entry<? extends K, ? extends V> entry) {
    return concat(Stream.of(entry));
  }

  public void forEach(BiConsumer<? super K, ? super V> consumer) {
    stream.forEach(Entries.tupled(consumer));
  }

  public boolean allMatch(BiPredicate<? super K, ? super V> p) {
    return stream.allMatch(Entries.tupled(p));
  }

  public boolean noneMatch(BiPredicate<? super K, ? super V> p) {
    return stream.noneMatch(Entries.tupled(p));
  }

  public boolean anyMatch(BiPredicate<? super K, ? super V> p) {
    return stream.anyMatch(Entries.tupled(p));
  }

  public Map<K, V> toMap() {
    return stream.collect(Collectors.toMap(Entries.getKey(), Entries.getValue()));
  }

  public Map<K, V> toMap(BinaryOperator<V> combiner) {
    return stream.collect(Collectors.toMap(Entries.getKey(), Entries.getValue(), combiner));
  }

  public ImmutableMap<K, V> toImmutableMap() {
    return stream.collect(ImmutableMap.toImmutableMap(Entries.getKey(), Entries.getValue()));
  }

  public ImmutableMap<K, V> toImmutableMap(BinaryOperator<V> mergeFunction) {
    return stream.collect(ImmutableMap.toImmutableMap(Entries.getKey(), Entries.getValue(), mergeFunction));
  }

  public ListMultimap<K, V> toMultimap(MultimapBuilder.ListMultimapBuilder<? super K, ? super V> builder) {
    return toMultimap(builder::build);
  }

  public SetMultimap<K, V> toMultimap(MultimapBuilder.SetMultimapBuilder<? super K, ? super V> builder) {
    return toMultimap(builder::build);
  }

  public <M extends Multimap<K, V>> M toMultimap(Supplier<M> multimapSupplier) {
    return stream.collect(Multimaps.toMultimap(Entries.getKey(), Entries.getValue(), multimapSupplier));
  }

  public SetMultimap<K, V> toImmutableSetMultimap() {
    return stream.collect(ImmutableSetMultimap.toImmutableSetMultimap(Entries.getKey(), Entries.getValue()));
  }

  public ListMultimap<K, V> toImmutableListMultimap() {
    return stream.collect(ImmutableListMultimap.toImmutableListMultimap(Entries.getKey(), Entries.getValue()));
  }

  public PersistentMap<K, V> toPersistentMap() {
    return toPersistentMap(PersistentMap.empty());
  }

  public PersistentMap<K, V> toPersistentMap(TriFunction<? super K, ? super V, ? super V, ? extends V> mergeFunction) {
    return toPersistentMap(PersistentMap.empty(), mergeFunction);
  }

  public PersistentMap<K, V> toPersistentMap(PersistentMap<K, V> startingMap) {
    return stream.collect(startingMap.entryCollector());
  }

  public PersistentMap<K, V> toPersistentMap(PersistentMap<K, V> startingMap, TriFunction<? super K, ? super V, ? super V, ? extends V> mergeFunction) {
    return stream.collect(startingMap.entryCollector(mergeFunction));
  }

  @Override
  public PairStream<K, V> filter(Predicate<? super Map.Entry<K, V>> predicate) {
    return of(stream.filter(predicate));
  }

  @Override
  public <R> Stream<R> map(Function<? super Map.Entry<K, V>, ? extends R> mapper) {
    return stream.map(mapper);
  }

  @Override
  public IntStream mapToInt(ToIntFunction<? super Map.Entry<K, V>> mapper) {
    return stream.mapToInt(mapper);
  }

  @Override
  public LongStream mapToLong(ToLongFunction<? super Map.Entry<K, V>> mapper) {
    return stream.mapToLong(mapper);
  }

  @Override
  public DoubleStream mapToDouble(ToDoubleFunction<? super Map.Entry<K, V>> mapper) {
    return stream.mapToDouble(mapper);
  }

  @Override
  public <R> Stream<R> flatMap(Function<? super Map.Entry<K, V>, ? extends Stream<? extends R>> mapper) {
    return stream.flatMap(mapper);
  }

  @Override
  public IntStream flatMapToInt(Function<? super Map.Entry<K, V>, ? extends IntStream> mapper) {
    return stream.flatMapToInt(mapper);
  }

  @Override
  public LongStream flatMapToLong(Function<? super Map.Entry<K, V>, ? extends LongStream> mapper) {
    return stream.flatMapToLong(mapper);
  }

  @Override
  public DoubleStream flatMapToDouble(Function<? super Map.Entry<K, V>, ? extends DoubleStream> mapper) {
    return stream.flatMapToDouble(mapper);
  }

  @Override
  public PairStream<K, V> distinct() {
    return of(stream.distinct());
  }

  @Override
  public PairStream<K, V> sorted() {
    return of(stream.sorted());
  }

  @Override
  public PairStream<K, V> sorted(Comparator<? super Map.Entry<K, V>> comparator) {
    return of(stream.sorted(comparator));
  }

  @Override
  public PairStream<K, V> peek(Consumer<? super Map.Entry<K, V>> action) {
    return of(stream.peek(action));
  }

  @Override
  public PairStream<K, V> limit(long maxSize) {
    return of(stream.limit(maxSize));
  }

  @Override
  public PairStream<K, V> skip(long n) {
    return of(stream.skip(n));
  }

  public PairStream<K, V> concat(Stream<? extends Map.Entry<? extends K, ? extends V>> other) {
    return of(Stream.concat(this, Reflect.blindCast(other)));
  }

  public PairStream<K, V> concat(Map<? extends K, ? extends V> other) {
    return concat(other.entrySet().stream());
  }

  @Override
  public void forEach(Consumer<? super Map.Entry<K, V>> action) {
    stream.forEach(action);
  }

  @Override
  public void forEachOrdered(Consumer<? super Map.Entry<K, V>> action) {
    stream.forEachOrdered(action);
  }

  @Override
  public Object[] toArray() {
    return stream.toArray();
  }

  @Override
  public <A> A[] toArray(IntFunction<A[]> generator) {
    return stream.toArray(generator);
  }

  @Override
  public Map.Entry<K, V> reduce(Map.Entry<K, V> identity, BinaryOperator<Map.Entry<K, V>> accumulator) {
    return stream.reduce(identity, accumulator);
  }

  @Override
  public Optional<Map.Entry<K, V>> reduce(BinaryOperator<Map.Entry<K, V>> accumulator) {
    return stream.reduce(accumulator);
  }

  @Override
  public <U> U reduce(U identity, BiFunction<U, ? super Map.Entry<K, V>, U> accumulator, BinaryOperator<U> combiner) {
    return stream.reduce(identity, accumulator, combiner);
  }

  public <U> U reduce(U identity, TriFunction<? super U, ? super K, ? super V, ? extends U> accumulator) {
    return stream.reduce(identity, (u, pair) -> accumulator.apply(u, pair.getKey(), pair.getValue()), (a, b) -> { throw new UnsupportedOperationException();});
  }

  @Override
  public <R> R collect(Supplier<R> supplier, BiConsumer<R, ? super Map.Entry<K, V>> accumulator, BiConsumer<R, R> combiner) {
    return stream.collect(supplier, accumulator, combiner);
  }

  @Override
  public <R, A> R collect(Collector<? super Map.Entry<K, V>, A, R> collector) {
    return stream.collect(collector);
  }

  @Override
  public Optional<Map.Entry<K, V>> min(Comparator<? super Map.Entry<K, V>> comparator) {
    return stream.min(comparator);
  }

  @Override
  public Optional<Map.Entry<K, V>> max(Comparator<? super Map.Entry<K, V>> comparator) {
    return stream.max(comparator);
  }

  @Override
  public long count() {
    return stream.count();
  }

  @Override
  public boolean anyMatch(Predicate<? super Map.Entry<K, V>> predicate) {
    return stream.anyMatch(predicate);
  }

  @Override
  public boolean allMatch(Predicate<? super Map.Entry<K, V>> predicate) {
    return stream.allMatch(predicate);
  }

  @Override
  public boolean noneMatch(Predicate<? super Map.Entry<K, V>> predicate) {
    return stream.noneMatch(predicate);
  }

  @Override
  public Optional<Map.Entry<K, V>> findFirst() {
    return stream.findFirst();
  }

  @Override
  public Optional<Map.Entry<K, V>> findAny() {
    return stream.findAny();
  }

  @Override
  public Iterator<Map.Entry<K, V>> iterator() {
    return stream.iterator();
  }

  @Override
  public Spliterator<Map.Entry<K, V>> spliterator() {
    return stream.spliterator();
  }

  @Override
  public boolean isParallel() {
    return stream.isParallel();
  }

  @Override
  public PairStream<K, V> sequential() {
    return of(stream.sequential());
  }

  @Override
  public PairStream<K, V> parallel() {
    return of(stream.parallel());
  }

  @Override
  public PairStream<K, V> unordered() {
    return of(stream.unordered());
  }

  @Override
  public PairStream<K, V> onClose(Runnable closeHandler) {
    return of(stream.onClose(closeHandler));
  }

  @Override
  public void close() {
    stream.close();
  }
}
