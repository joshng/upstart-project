package upstart.util.functions;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

public class MoreFunctions {
  @SuppressWarnings("unchecked")
  public static <T> Consumer<T> noopConsumer() {
    return (Consumer<T>) NoopConsumer.Instance;
  }

  public static <A, B> Predicate<A> onResultOf(Function<A, B> function, Predicate<? super B> predicate) {
    return a -> predicate.test(function.apply(a));
  }

  public static <T> UnaryOperator<T> tap(Consumer<? super T> tapper) {
    return item -> {
      tapper.accept(item);
      return item;
    };
  }

  public static <I, O> Supplier<O> bind(I input, Function<I, O> fn) {
    return () -> fn.apply(input);
  }

  public static <T> Predicate<T> notEqual(T value) {
    return t -> !value.equals(t);
  }

  private enum NoopConsumer implements Consumer<Object> {
    Instance;

    @Override
    public void accept(Object o) {
    }
  }
}
