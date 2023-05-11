package upstart.util.context;

import java.util.Optional;

public interface AsyncContextManager<T> {
  Optional<T> captureSnapshot();

  void restoreSnapshot(T value);

  void mergeApplyFromSnapshot(T value);

  void remove();

  T mergeSnapshots(T mergeTo, T mergeFrom);
}
