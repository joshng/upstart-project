package upstart.services;

import java.util.concurrent.CompletableFuture;

public abstract class AsyncService extends NotifyingService {
  protected abstract CompletableFuture<?> startUp() throws Exception;
  protected abstract CompletableFuture<?> shutDown() throws Exception;

  @Override
  protected void doStart() {
    try {
      startWith(startUp());
    } catch (Exception e) {
      notifyFailed(e);
    }
  }

  @Override
  protected void doStop() {
    try {
      stopWith(shutDown());
    } catch (Exception e) {
      notifyFailed(e);
    }
  }
}
