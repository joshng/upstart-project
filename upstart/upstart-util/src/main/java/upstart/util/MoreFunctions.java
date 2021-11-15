package upstart.util;

import java.util.function.Function;
import java.util.function.Predicate;

public class MoreFunctions {
  public static <A, B> Predicate<A> onResultOf(Function<A, B> function, Predicate<? super B> predicate) {
    return a -> predicate.test(function.apply(a));
  }
}
