package upstart.util.concurrent;

import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;

public class AtomicMutableReference<T> extends AtomicReference<T> implements MutableReference<T>, Callable<T> {
  public AtomicMutableReference(T initialValue) {
    super(initialValue);
  }

  public AtomicMutableReference() {
  }

  public static <T> AtomicMutableReference<T> newReference(T initialValue) {
    return new AtomicMutableReference<>(initialValue);
  }

  public static <T> AtomicMutableReference<T> newNullReference() {
    return new AtomicMutableReference<>();
  }

}
