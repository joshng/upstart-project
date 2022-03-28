package upstart.util.functions;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

public class MoreFunctions {
  public static <A, B> Predicate<A> onResultOf(Function<A, B> function, Predicate<? super B> predicate) {
    return a -> predicate.test(function.apply(a));
  }

  public static <T> UnaryOperator<T> tap(Consumer<? super T> tapper) {
    return item -> {
      tapper.accept(item);
      return item;
    };
  }
}
