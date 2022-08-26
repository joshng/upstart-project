package upstart.util.concurrent;

public class SimpleReference<T> implements MutableReference<T> {
  public SimpleReference(T value) {
    this.value = value;
  }

  public SimpleReference() {
    this(null);
  }

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
