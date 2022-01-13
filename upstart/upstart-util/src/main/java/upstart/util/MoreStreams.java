package upstart.util;

import com.google.common.collect.AbstractIterator;
import com.google.common.collect.Streams;
import upstart.util.concurrent.Promise;

import java.util.Iterator;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

public class MoreStreams {
  /**
   * Performs the same computation as {@link Stream#reduce}, but instead of returning only the final value,
   * this method returns a {@link Stream} yielding the results of each intermediate step
   * (also similar to {@link Stream#map}, but with incremental state).<p/>
   */
  public static <O, T> Stream<O> scan(O identity, Stream<T> stream, BiFunction<? super O, ? super T, ? extends O> visitor) {
    return stream.sequential().map(new Function<T, O>() {
      O memo = identity;
      @Override
      public O apply(T t) {
        return memo = visitor.apply(memo, t);
      }
    });
  }

  public static <T> Stream<T> generate(Supplier<T> stepFunction) {
    return Stream.generate(stepFunction).takeWhile(Objects::nonNull);
  }

  public static <T> Stream<T> generate(T firstValue, UnaryOperator<T> stepFunction) {
    return Stream.iterate(firstValue, Objects::nonNull, stepFunction);
  }

  public static <T> Stream<T> append(Stream<? extends T> stream, T lastItem) {
    return Stream.concat(stream, Stream.of(lastItem));
  }

  @SafeVarargs
  public static <T> Stream<T> append(Stream<? extends T> stream, T... lastItems) {
    return Stream.concat(stream, Stream.of(lastItems));
  }

  public static <T> Stream<T> prepend(T firstItem, Stream<? extends T> stream) {
    return Stream.concat(Stream.of(firstItem), stream);
  }

  public static <I, O extends I> Stream<O> filter(Stream<I> inputs, Class<O> filterClass) {
    return inputs.filter(filterClass::isInstance).map(filterClass::cast);
  }

  public static <I, O> O foldLeft(O identity, Stream<I> stream, BiFunction<? super O, ? super I, ? extends O> combiner) {
    O memo = identity;
    Iterable<I> iter = stream::iterator; // pathetic that this can't be inlined. substitution principal, java8!?
    for (I item : iter) {
      memo = combiner.apply(memo, item);
    }
    return memo;
  }

  public static <I> Pair<Stream<I>, CompletableFuture<Stream<I>>> span(Stream<I> stream, Predicate<? super I> takeWhile) {
    Promise<Stream<I>> rest = new Promise<>();
    Stream<I> take = Streams.stream(new AbstractIterator<I>() {
      private final Iterator<I> allElements = stream.iterator();

      @Override
      protected I computeNext() {
        if (allElements.hasNext()) {
          I next = allElements.next();
          if (takeWhile.test(next)) {
            return next;
          } else {
            rest.complete(prepend(next, Streams.stream(allElements)));
            return endOfData();
          }
        } else {
          rest.complete(Stream.empty());
          return endOfData();
        }
      }
    });
    return Pair.of(take, rest);
  }

  public static <T> Stream<Stream<T>> partition(int size, Stream<T> stream) {
    Iterator<T> iterator = stream.iterator();
    return Stream.iterate(
            Streams.stream(iterator).limit(size),
            ignored -> iterator.hasNext(),
            ignored -> Streams.stream(iterator).limit(size)
    );
  }

  public abstract static class RecursiveIterator<T> implements Iterator<T> {
    private T next;

    public RecursiveIterator(T first) {
      this.next = first;
    }

    /**
     * @param current the previously-returned value
     * @return the value to return after current, or null to end the stream
     */
    protected abstract T computeNext(T current);

    public static <T> RecursiveIterator<T> from(T initialValue, UnaryOperator<T> stepFunction) {
      return new RecursiveIterator<T>(initialValue) {
        @Override
        protected T computeNext(T current) {
          return stepFunction.apply(current);
        }
      };
    }

    @Override
    public boolean hasNext() {
      return next != null;
    }

    @Override
    public T next() {
      T result = next;
      next = computeNext(result);
      return result;
    }
  }
}
