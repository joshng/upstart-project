package upstart.util.concurrent;

import com.google.common.util.concurrent.MoreExecutors;
import upstart.util.Nothing;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Provides a single {@link CompletableFuture} that represents the completion of a stream of {@link CompletableFuture Futures},
 * without regard to whether they succeed or fail. Particularly useful in shutdown scenarios, where you want to know
 * when all asynchronous jobs have completed, but aren't concerned with their outcomes. If you do care about results or failures,
 * consider using a {@link FutureSuccessTracker} or {@link ParallelFold} instead.<p/>
 * <p>
 * Example usage:
 * <p>
 * <pre>{@code
 * FutureCompletionTracker tracker = new FutureCompletionTracker();
 * <p>
 * for (SomeInput input : inputs) {
 *     tracker.track(startSomeJob(input));
 * }
 * <p>
 * CompletableFuture<Nothing> completionFuture = tracker.setNoMoreJobs();
 * <p>
 * completionFuture.join(); // waits until all jobs have completed
 * <p>
 *  // ... //
 * <p>
 * CompletableFuture<Something> startSomeJob(SomeInput input) { ... }
 * }</pre>
 */
public class FutureCompletionTracker extends AbstractIndependentCompletionTracker<Object, Nothing> {
  public FutureCompletionTracker() {
    super(MoreExecutors.directExecutor());
  }

  @Override
  protected void handleCompletedJob(CompletionStage<?> job) throws Exception {
  }

  @Override
  protected Nothing computeResult() throws Exception {
    return Nothing.NOTHING;
  }
}

