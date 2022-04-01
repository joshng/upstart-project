package upstart.util.concurrent;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public abstract class AbstractCompletionTracker<I, O> {
  private final Executor jobCompletionExecutor;
  private final Promise<O> completionPromise = new Promise<>();
  private final AtomicLong startedCount = new AtomicLong();
  private final AtomicLong completedCount = new AtomicLong();
  private final AtomicBoolean markedComplete = new AtomicBoolean();

  public AbstractCompletionTracker(Executor jobCompletionExecutor) {
    this.jobCompletionExecutor = jobCompletionExecutor;
  }

  protected abstract void handleCompletedJob(CompletionStage<? extends I> job) throws Exception;

  protected abstract O computeResult() throws Exception;

  /**
   * @return A {@link CompletableFuture} that completes when all {@link #track tracked} jobs are completed, and the
   * resulting final {@link #computeResult computation} is done.
   */
  public Promise<O> completionFuture() {
    return completionPromise;
  }

  public boolean isAcceptingNewJobs() {
    return !isDone();
  }

  public boolean isDone() {
    return completionPromise.isDone();
  }

  public <F extends CompletionStage<? extends I>> F track(final F job) {
    startedCount.incrementAndGet();
    if (!isAcceptingNewJobs()) job.toCompletableFuture().cancel(false);
    job.whenCompleteAsync((r, x) -> {
      try {
        handleCompletedJob(job);
      } catch (Exception e) {
        abort(e);
      } finally {
        completedCount.incrementAndGet();
        checkDone();
      }
    }, jobCompletionExecutor);
    return job;
  }

  /**
   * Completes the completion-future with the given exception, and causes all subsequently-submitted
   * jobs to be rejected/cancelled.
   *
   * @return true if this exception was applied to the completion of this tracker
   */
  protected boolean abort(Throwable e) {
    return completionPromise.completeExceptionally(e);
  }

  public long incompleteJobCount() {
    return startedCount.get() - completedCount.get();
  }

  public long getCompletedJobCount() {
    return completedCount.get();
  }

  protected void checkDone() {
    if (allJobsDone() && markedComplete.compareAndSet(false, true)) {
      completionPromise.tryComplete(this::computeResult);
    }
  }

  protected boolean allJobsDone() {
    return incompleteJobCount() == 0;
  }
}

