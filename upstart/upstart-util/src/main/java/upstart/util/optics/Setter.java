package upstart.util.optics;

public interface Setter<T, V> {
  T set(T instance, V value);
}
