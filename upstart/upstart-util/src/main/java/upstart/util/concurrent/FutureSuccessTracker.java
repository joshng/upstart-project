package upstart.util.concurrent;

/**
 * Created by: josh 10/11/13 6:05 PM
 */

import upstart.util.Nothing;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.Stream;

/**
 * A {@link FutureCompletionTracker} that aborts if any {@link #track submitted} job fails.
 * The {@link CompletableFuture} returned by {@link #completionFuture()} (and {@link #setNoMoreJobs()}) will
 * reflect the exception thrown by the first failed job, if any.
 */
public class FutureSuccessTracker extends FutureCompletionTracker {
  public static CompletableFuture<Nothing> allOf(Stream<? extends CompletionStage<?>> futures) {
    var tracker = new FutureSuccessTracker();
    futures.filter(f -> !CompletableFutures.isCompletedNormally(f.toCompletableFuture())).forEach(tracker::track);
    return tracker.setNoMoreJobs();
  }

  public FutureSuccessTracker() {
    super();
  }

  @Override
  protected void handleCompletedJob(CompletionStage<?> job) throws Exception {
    job.toCompletableFuture().join();
  }
}

