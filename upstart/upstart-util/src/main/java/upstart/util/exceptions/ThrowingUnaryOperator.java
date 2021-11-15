package upstart.util.exceptions;

public interface ThrowingUnaryOperator<T> extends ThrowingFunction<T, T> {
  static <T> ThrowingUnaryOperator<T> identity() {
    return t -> t;
  }
}
