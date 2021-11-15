package upstart.util.concurrent;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.google.common.base.Preconditions.checkState;

public abstract class AbstractIndependentCompletionTracker<I, O> extends AbstractCompletionTracker<I,O> {
  private final AtomicBoolean noMore = new AtomicBoolean();

  public AbstractIndependentCompletionTracker(Executor jobCompletionExecutor) {
    super(jobCompletionExecutor);
  }

  @Override
  public boolean isAcceptingNewJobs() {
    return !noMore.get() && super.isAcceptingNewJobs();
  }

  /**
   * Arranges for the {@link #completionFuture()} to complete when all previously-submitted futures are done.
   * <p/>
   *
   * After invoking this method, subsequently-submitted jobs will be {@link CompletableFuture#cancel canceled}
   * upon submission, which may influence the outcome observed by this tracker's {@link #handleCompletedJob} callback
   * for such canceled futures (and thus influence the eventual state of the {@link #completionFuture()}, depending on
   * the concrete implementation of this {@link AbstractIndependentCompletionTracker}).
   *
   * @return the {@link #completionFuture()}, which completes when all previously-submitted futures complete (if not sooner).
   */
  public CompletableFuture<O> setNoMoreJobs() {
    checkState(noMore.compareAndSet(false, true), "Called setNoMoreJobs more than once");
    checkDone();
    return completionFuture();
  }

  @Override
  protected boolean allJobsDone() {
    return !isAcceptingNewJobs() && super.allJobsDone();
  }
}

