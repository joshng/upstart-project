package upstart.util.context;

import java.util.Optional;

public interface AsyncContextManager<T> {
  Optional<T> captureSnapshot();

  void restoreSnapshot(T value);

  void remove();

  default T merge(T a, T b) {
    return b;
  }
}
