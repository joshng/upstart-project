package upstart.util.context;

import java.util.Optional;

public interface AsyncContextManager<T> {
  Optional<T> captureSnapshot();
  void restoreFromSnapshot(T value);
}
