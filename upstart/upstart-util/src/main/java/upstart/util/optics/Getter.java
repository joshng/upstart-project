package upstart.util.optics;

public interface Getter<T, V> {
  V get(T instance);

  default <U> Getter<T, U> andThenGet(Getter<V, U> next) {
    return instance -> next.get(get(instance));
  }
}
