package upstart.util.collect;

import upstart.util.functions.QuadFunction;
import upstart.util.functions.TriFunction;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.concurrent.Callable;
import java.util.function.BiFunction;
import java.util.function.BinaryOperator;
import java.util.function.Consumer;
import java.util.function.DoubleFunction;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.function.LongFunction;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

public class Optionals {
  @SuppressWarnings("unchecked")
  public static <T> Optional<T> asInstance(Object obj, Class<T> klass) {
    return klass.isInstance(obj) ? Optional.of((T) obj) : Optional.empty();
  }

  public static <T> Optional<T> onlyIf(boolean condition, @Nullable T value) {
    return condition ? Optional.ofNullable(value) : Optional.empty();
  }

  public static <T> Optional<T> onlyIfFrom(boolean condition, Supplier<T> value) {
    return condition ? Optional.ofNullable(value.get()) : Optional.empty();
  }

  public static <T, U> Optional<U> filter(Class<U> filteredClass, Optional<T> optional) {
    return optional.filter(filteredClass::isInstance).map(filteredClass::cast);
  }

  // this is an instance-method on the JDK Optional class in java 9, but missing in java 8
  public static <T> void ifPresentOrElse(Optional<T> optional, Consumer<? super T> ifPresent, Runnable orElse) {
    optional.ifPresentOrElse(ifPresent, orElse);
  }

  @SuppressWarnings("unchecked")
  public static <T> Optional<T> or(Optional<? extends T> optional, Optional<? extends T> alternative) {
    return (Optional<T>) (optional.isPresent() ? optional : alternative);
  }

  @SuppressWarnings("unchecked")
  public static <T> Optional<T> merge(Optional<? extends T> first, Optional<? extends T> second, BinaryOperator<T> merge) {
    return first.map(aa -> second.map(bb -> merge.apply(aa, bb)).or(() -> first)).orElse((Optional<T>) second);
  }
  
  public static <T> Optional<T> mapToObj(OptionalInt optionalInt, IntFunction<? extends T> mapper) {
    return optionalInt.isPresent() ? Optional.of(mapper.apply(optionalInt.getAsInt())) : Optional.empty();
  }
  
  public static <T> Optional<T> mapToObj(OptionalLong optionalLong, LongFunction<? extends T> mapper) {
    return optionalLong.isPresent() ? Optional.of(mapper.apply(optionalLong.getAsLong())) : Optional.empty();
  }

  public static <T> Optional<T> mapToObj(OptionalDouble optionalDouble, DoubleFunction<? extends T> mapper) {
    return optionalDouble.isPresent() ? Optional.of(mapper.apply(optionalDouble.getAsDouble())) : Optional.empty();
  }

  /**
   * @see Stream#allMatch
   */
  public static <T> boolean allMatch(Optional<T> optional, Predicate<? super T> predicate) {
    return optional.isEmpty() || optional.filter(predicate).isPresent();
  }

  /**
   * @see Stream#anyMatch
   */
  public static <T> boolean anyMatch(Optional<T> optional, Predicate<? super T> predicate) {
    return optional.isPresent() && predicate.test(optional.orElseThrow());
  }

  /**
   * @see Stream#noneMatch
   */
  public static <T> boolean noneMatch(Optional<T> optional, Predicate<? super T> predicate) {
    return optional.isEmpty() || !predicate.test(optional.orElseThrow());
  }

  public static <T> Optional<T> getWithinBounds(List<T> list, int index) {
    return onlyIfFrom(index >= 0 && index < list.size(), () -> list.get(index));
  }

  public static <T> Optional<T> exceptionAsOptional(Callable<T> callable) {
    try {
      return Optional.of(callable.call());
    } catch (Exception e) {
      return Optional.empty();
    }
  }

  public static <A, B, O> Optional<O> zip(
          Optional<? extends A> a,
          Optional<? extends B> b,
          BiFunction<? super A, ? super B, ? extends O> zipper
  ) {
    return a.isPresent() && b.isPresent()
            ? Optional.of(zipper.apply(a.get(), b.get()))
            : Optional.empty();
  }

  public static <A, B, O> BiFunction<Optional<A>, Optional<B>, Optional<O>> zipper(BiFunction<? super A, ? super B, ? extends O> zipper) {
    return (a, b) -> zip(a, b, zipper);
  }

  public static <A, B, O> BiFunction<Optional<A>, Optional<B>, Optional<O>> flatZipper(BiFunction<? super A, ? super B, ? extends Optional<O>> zipper) {
    return (a, b) -> flatZip(a, b, zipper);
  }

  public static <A, B, O> Optional<O> flatZip(
          Optional<? extends A> a,
          Optional<? extends B> b,
          BiFunction<? super A, ? super B, ? extends Optional<O>> zipper
  ) {
    return a.isPresent() && b.isPresent()
            ? zipper.apply(a.get(), b.get())
            : Optional.empty();
  }

  public static <A, B, C, O> Optional<O> zip(
          Optional<A> a,
          Optional<B> b,
          Optional<C> c,
          TriFunction<? super A, ? super B, ? super C, ? extends O> zipper
  ) {
    return a.isPresent() && b.isPresent() && c.isPresent()
            ? Optional.of(zipper.apply(a.get(), b.get(), c.get()))
            : Optional.empty();
  }

  public static <A, B, C, O> Optional<O> flatZip(
          Optional<A> a,
          Optional<B> b,
          Optional<C> c,
          TriFunction<? super A, ? super B, ? super C, ? extends Optional<O>> zipper
  ) {
    return a.isPresent() && b.isPresent() && c.isPresent()
            ? zipper.apply(a.get(), b.get(), c.get())
            : Optional.empty();
  }

  public static <A, B, C, D, O> Optional<O> zip(
          Optional<A> a,
          Optional<B> b,
          Optional<C> c,
          Optional<D> d,
          QuadFunction<? super A, ? super B, ? super C, ? super D, ? extends O> zipper
  ) {
    return a.isPresent() && b.isPresent() && c.isPresent() && d.isPresent()
            ? Optional.of(zipper.apply(a.get(), b.get(), c.get(), d.get()))
            : Optional.empty();
  }

  public static <A, B, C, D, O> Optional<O> flatZip(
          Optional<A> a,
          Optional<B> b,
          Optional<C> c,
          Optional<D> d,
          QuadFunction<? super A, ? super B, ? super C, ? super D, ? extends Optional<O>> zipper
  ) {
    return a.isPresent() && b.isPresent() && c.isPresent() && d.isPresent()
            ? zipper.apply(a.get(), b.get(), c.get(), d.get())
            : Optional.empty();
  }

  public static <I, O> Function<Optional<? extends I>, Optional<O>> liftFunction(Function<I ,O> f) {
    return optional -> optional.map(f);
  }

  public static <I, O> Function<Optional<? extends I>, Optional<O>> liftOptionalFunction(Function<I, Optional<O>> f) {
    return optional -> optional.flatMap(f);
  }
}
