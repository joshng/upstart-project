package upstart.util.concurrent.services.test;

import upstart.util.concurrent.services.IdleService;
import upstart.util.concurrent.CompletableFutures;
import upstart.util.concurrent.Promise;

public class ChoreographedLifecycleService extends IdleService {
  public final TransitionGate startupGate = new TransitionGate();
  public final TransitionGate shutdownGate = new TransitionGate();


  public void fail(Throwable e) {
    notifyFailed(e);
  }

  @Override
  protected void startUp() throws Exception {
    startupGate.transition();
  }

  @Override
  protected void shutDown() throws Exception {
    shutdownGate.transition();
  }

  public static class TransitionGate {
    public final Promise<Void> requested = new Promise<>();
    public final Promise<Void> ready = new Promise<>();

    public void open() {
      ready.complete(null);
    }

    public void prepareFailure(Throwable e) {
      ready.completeExceptionally(e);
    }

    private void transition() {
      requested.complete(null);
      ready.join();
    }


    public boolean isRequested() {
      return CompletableFutures.isCompletedNormally(requested);
    }
  }
}
