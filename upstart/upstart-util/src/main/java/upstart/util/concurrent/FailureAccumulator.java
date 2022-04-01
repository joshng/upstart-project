package upstart.util.concurrent;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class FailureAccumulator {
  private final int maxRecordedFailures;
  private final Set<Throwable> failures;
  private final AtomicMutableReference<Throwable> pendingFailure = new AtomicMutableReference<>();


  public FailureAccumulator(int maxRecordedFailures) {
    this.maxRecordedFailures = maxRecordedFailures;
    failures = ConcurrentHashMap.newKeySet(this.maxRecordedFailures);
  }

  public boolean accumulate(Throwable cause) {
    if (failures.size() >= maxRecordedFailures || !failures.add(cause)) return false;
    while (true) {
      Throwable prev = pendingFailure.get();
      if (prev != null) {
        if (prev != cause) prev.addSuppressed(cause);
        return false;
      } else if (pendingFailure.compareAndSet(null, cause)) {
        return true;
      }
    }
  }

  public Optional<Throwable> accumulatedFailure() {
    return Optional.ofNullable(pendingFailure.get());
  }
}
