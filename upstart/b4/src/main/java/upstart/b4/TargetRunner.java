package upstart.b4;

import upstart.guice.PrivateBinding;
import upstart.util.BooleanChoice;
import upstart.util.Nothing;
import upstart.util.exceptions.ThrowingRunnable;
import upstart.util.concurrent.Promise;

import javax.inject.Inject;
import java.util.concurrent.CompletableFuture;

class TargetRunner {
  private static final String ABORTED_STATUS = B4Cli.unhealthyHighlight("aborted");
  private static final String FAILED_STATUS = B4Cli.unhealthyHighlight("FAILED!");
  public static final String DONE_STATUS = B4Cli.healthyHighlight(" done  ");
  private final B4TargetContext context;
  private final Object config;
  private final B4Function<Object> function;
  private final Promise<Nothing> cleanFuture = new Promise<>();
  private final Promise<Nothing> runFuture = new Promise<>();

  @SuppressWarnings("unchecked")
  @Inject
  TargetRunner(@PrivateBinding Object config, B4Function function, B4TargetContext context) {
    this.context = context;
    this.config = config;
    this.function = function;
  }

  CompletableFuture<Nothing> runFuture() {
    return runFuture;
  }

  CompletableFuture<Nothing> cleanFuture() {
    return cleanFuture;
  }

  boolean runOnSeparateThread() {
    return function.runOnSeparateThread();
  }

  CompletableFuture<?> clean(Object ignored) {
    return completePromise(cleanFuture, () -> function.clean(config, context));
  }

  CompletableFuture<?> run(Object ignored) {
    return completePromise(runFuture, () -> function.run(config, context))
            .thenRun(() -> context.say("DONE:", displayName()));
  }

  private Promise<Nothing> completePromise(Promise<Nothing> promise, ThrowingRunnable completion) {
    return promise.tryComplete(Nothing.NOTHING, () -> {
      try {
        completion.runOrThrow();
      } catch (Throwable e) {
        if (!B4CancellationException.isCancellation(e)) {
          throw new B4TaskFailedException(targetInstanceId(), e);
        } else {
          throw e;
        }
      }
    });
  }

  private String displayName() {
    return targetInstanceId().displayName();
  }

  private TargetInstanceId targetInstanceId() {
    return context.getInvocation().id();
  }

  void cancel(Exception cancellationException) {
    runFuture.completeExceptionally(cancellationException);
    cleanFuture.completeExceptionally(cancellationException);
    function.cancel();
    context.cancel();
  }

  @Override
  public String toString() {
    String cleanStatus = describeStatus(context.activePhases().doClean, cleanFuture);
    String runStatus = describeStatus(context.activePhases().doRun, runFuture);
    return String.format("%s\nclean:%s \n  run:%s ", targetInstanceId(), cleanStatus, runStatus);
  }

  private String describeStatus(boolean enabled, Promise<Nothing> future) {
    return BooleanChoice.of(!enabled, " skipped ")
            .or(!future.isDone(), "(pending)")
            .or(!future.isCompletedExceptionally(), DONE_STATUS)
            .or(future.isCancelled(), ABORTED_STATUS)
            .otherwise(FAILED_STATUS);
  }
}
