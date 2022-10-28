package upstart.util.collect;

import upstart.util.functions.TriFunction;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.BiFunction;
import java.util.function.BinaryOperator;
import java.util.function.Consumer;
import java.util.function.Function;
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

  /**
   * @see Stream#allMatch
   */
  public static <T> boolean allMatch(Optional<T> optional, Predicate<? super T> predicate) {
    return optional.isEmpty() || optional.filter(predicate).isPresent();
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
          Optional<A> a,
          Optional<B> b,
          BiFunction<? super A, ? super B, ? extends O> zipper
  ) {
    return a.flatMap(aa -> b.map(bb -> zipper.apply(aa, bb)));
  }

  public static <A, B, C, O> Optional<O> zip(
          Optional<A> a,
          Optional<B> b,
          Optional<C> c,
          TriFunction<? super A, ? super B, ? super C, ? extends O> zipper
  ) {
    return a.flatMap(aa -> b.flatMap(bb -> c.map(cc -> zipper.apply(aa, bb, cc))));
  }

  public static <I, O> Function<Optional<? extends I>, Optional<O>> liftFunction(Function<I ,O> f) {
    return optional -> optional.map(f);
  }

  public static <I, O> Function<Optional<? extends I>, Optional<O>> liftOptionalFunction(Function<I, Optional<O>> f) {
    return optional -> optional.flatMap(f);
  }
}
