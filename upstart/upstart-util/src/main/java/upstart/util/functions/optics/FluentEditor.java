package upstart.util.functions.optics;

public class FluentEditor<T> {
  private final T result;

  public FluentEditor(T result) {
    this.result = result;
  }

  public <V> FluentEditor<T> edit(Lens<T, V> lens, V value) {
    return new FluentEditor<>(lens.set(result, value));
  }

  public T result() {
    return result;
  }
}
