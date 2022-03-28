package upstart.util.collect;

import com.google.common.collect.Streams;

import java.util.Comparator;
import java.util.Iterator;
import java.util.Optional;
import java.util.Spliterator;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
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
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;

public interface OneTimeIterableStream<T> extends Stream<T>, Iterable<T> {
  @Override
  void forEach(Consumer<? super T> action);

  @Override
  Spliterator<T> spliterator();

  static <T> OneTimeIterableStream<T> of(Iterable<T> iterable) {
    return of(Streams.stream(iterable));
  }

  static <T> OneTimeIterableStream<T> of(Iterator<T> iterable) {
    return of(Streams.stream(iterable));
  }

  static <T> OneTimeIterableStream<T> of(Stream<T> stream) {
    if (stream instanceof OneTimeIterableStream) {
      return (OneTimeIterableStream<T>) stream;
    }
    return new OneTimeIterableStream<>() {
      @Override
      public Iterator<T> iterator() {
        return stream.iterator();
      }

      @Override
      public Spliterator<T> spliterator() {
        return stream.spliterator();
      }

      @Override
      public boolean isParallel() {
        return stream.isParallel();
      }

      @Override
      public Stream<T> sequential() {
        return stream.sequential();
      }

      @Override
      public Stream<T> parallel() {
        return stream.parallel();
      }

      @Override
      public Stream<T> unordered() {
        return stream.unordered();
      }

      @Override
      public OneTimeIterableStream<T> onClose(Runnable closeHandler) {
        return of(stream.onClose(closeHandler));
      }

      @Override
      public void close() {
        stream.close();
      }

      public void forEach(Consumer<? super T> action) {
        stream.forEach(action);
      }

      public T reduce(T identity, BinaryOperator<T> accumulator) {
        return stream.reduce(identity, accumulator);
      }

      public Stream<T> filter(Predicate<? super T> predicate) {
        return stream.filter(predicate);
      }

      public <R> Stream<R> map(Function<? super T, ? extends R> mapper) {
        return stream.map(mapper);
      }

      public IntStream mapToInt(ToIntFunction<? super T> mapper) {
        return stream.mapToInt(mapper);
      }

      public LongStream mapToLong(ToLongFunction<? super T> mapper) {
        return stream.mapToLong(mapper);
      }

      public DoubleStream mapToDouble(ToDoubleFunction<? super T> mapper) {
        return stream.mapToDouble(mapper);
      }

      public <R> Stream<R> flatMap(Function<? super T, ? extends Stream<? extends R>> mapper) {
        return stream.flatMap(mapper);
      }

      public IntStream flatMapToInt(Function<? super T, ? extends IntStream> mapper) {
        return stream.flatMapToInt(mapper);
      }

      public LongStream flatMapToLong(Function<? super T, ? extends LongStream> mapper) {
        return stream.flatMapToLong(mapper);
      }

      public DoubleStream flatMapToDouble(Function<? super T, ? extends DoubleStream> mapper) {
        return stream.flatMapToDouble(mapper);
      }

      public Stream<T> distinct() {
        return stream.distinct();
      }

      public Stream<T> sorted() {
        return stream.sorted();
      }

      public Stream<T> sorted(Comparator<? super T> comparator) {
        return stream.sorted(comparator);
      }

      public Stream<T> peek(Consumer<? super T> action) {
        return stream.peek(action);
      }

      public Stream<T> limit(long maxSize) {
        return stream.limit(maxSize);
      }

      public Stream<T> skip(long n) {
        return stream.skip(n);
      }

      public Stream<T> takeWhile(Predicate<? super T> predicate) {
        return stream.takeWhile(predicate);
      }

      public Stream<T> dropWhile(Predicate<? super T> predicate) {
        return stream.dropWhile(predicate);
      }

      public void forEachOrdered(Consumer<? super T> action) {
        stream.forEachOrdered(action);
      }

      public Object[] toArray() {
        return stream.toArray();
      }

      public <A> A[] toArray(IntFunction<A[]> generator) {
        return stream.toArray(generator);
      }

      public Optional<T> reduce(BinaryOperator<T> accumulator) {
        return stream.reduce(accumulator);
      }

      public <U> U reduce(U identity, BiFunction<U, ? super T, U> accumulator, BinaryOperator<U> combiner) {
        return stream.reduce(identity, accumulator, combiner);
      }

      public <R> R collect(Supplier<R> supplier, BiConsumer<R, ? super T> accumulator, BiConsumer<R, R> combiner) {
        return stream.collect(supplier, accumulator, combiner);
      }

      public <R, A> R collect(Collector<? super T, A, R> collector) {
        return stream.collect(collector);
      }

      public Optional<T> min(Comparator<? super T> comparator) {
        return stream.min(comparator);
      }

      public Optional<T> max(Comparator<? super T> comparator) {
        return stream.max(comparator);
      }

      public long count() {
        return stream.count();
      }

      public boolean anyMatch(Predicate<? super T> predicate) {
        return stream.anyMatch(predicate);
      }

      public boolean allMatch(Predicate<? super T> predicate) {
        return stream.allMatch(predicate);
      }

      public boolean noneMatch(Predicate<? super T> predicate) {
        return stream.noneMatch(predicate);
      }

      public Optional<T> findFirst() {
        return stream.findFirst();
      }

      public Optional<T> findAny() {
        return stream.findAny();
      }
    };
  }
}
