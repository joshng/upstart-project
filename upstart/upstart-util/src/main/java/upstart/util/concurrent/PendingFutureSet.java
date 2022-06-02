package upstart.util.concurrent;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class PendingFutureSet<T> {
  private final Set<CompletableFuture<? extends T>> pendingFutures = ConcurrentHashMap.newKeySet();

  public <F extends CompletableFuture<? extends T>> F track(F future) {
    isPending(future);
    return future;
  }

  public boolean isPending(CompletableFuture<? extends T> future) {
    boolean add = !future.isDone();
    if (add) {
      pendingFutures.add(future);
      future.whenComplete((ignored, e) -> pendingFutures.remove(future));
    }
    return add;
  }

  public Set<CompletableFuture<? extends T>> pendingFutures() {
    return Collections.unmodifiableSet(pendingFutures);
  }

  public CompletableFuture<Void> flush() {
    return CompletableFutures.allOf(pendingFutures.stream());
  }

  public int pendingFutureCount() {
    return pendingFutures.size();
  }
}
