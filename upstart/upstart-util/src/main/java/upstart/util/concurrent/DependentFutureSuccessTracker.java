package upstart.util.concurrent;

import java.util.concurrent.CompletionStage;

public class DependentFutureSuccessTracker extends DependentFutureCompletionTracker {
  @Override
  protected void handleCompletedJob(CompletionStage<?> job) throws Exception {
    job.toCompletableFuture().join();
  }
}
