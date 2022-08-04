package upstart.util.concurrent;

public class SimpleReference<T> implements MutableReference<T> {
  private T value;
  @Override
  public void set(T value) {
    this.value = value;
  }

  @Override
  public T get() {
    return value;
  }
}
