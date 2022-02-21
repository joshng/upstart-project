package upstart.util.concurrent;

import com.google.common.util.concurrent.MoreExecutors;
import upstart.util.Nothing;

import java.util.concurrent.CompletionStage;

public class DependentFutureCompletionTracker extends AbstractCompletionTracker<Object, Nothing> {
  public DependentFutureCompletionTracker() {
    super(MoreExecutors.directExecutor());
  }

  @Override
  protected void handleCompletedJob(CompletionStage<?> job) throws Exception {
  }

  @Override
  protected Nothing computeResult() {
    return Nothing.NOTHING;
  }
}