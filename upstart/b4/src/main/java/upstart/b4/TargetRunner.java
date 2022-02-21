package upstart.b4;

import upstart.guice.PrivateBinding;
import upstart.util.BooleanChoice;
import upstart.util.Nothing;
import upstart.util.exceptions.ThrowingRunnable;
import upstart.util.concurrent.Promise;

import javax.inject.Inject;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

class TargetRunner {
  private static final String ABORTED_STATUS = B4Console.unhealthyHighlight("aborted ");
  private static final String FAILED_STATUS = B4Console.unhealthyHighlight("FAILED! ");
  public static final String DONE_STATUS = B4Console.healthyHighlight("  done  ");
  public static final String PENDING_STATUS = B4Console.noticeHighlight("running..");
  private static final String SKIPPED_STATUS = B4Console.noticeLowlight("skipped ");
  private final B4TaskContext context;
  private final Object config;
  private final B4Function<Object> function;
  private final Promise<Nothing> cleanFuture = new Promise<>();
  private final Promise<Nothing> runFuture = new Promise<>();
  private final AtomicBoolean cleanStarted = new AtomicBoolean(false);
  private final AtomicBoolean runStarted = new AtomicBoolean(false);

  @SuppressWarnings("unchecked")
  @Inject
  TargetRunner(@PrivateBinding Object config, B4Function function, B4TaskContext context) {
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
    if (!cleanStarted.getAndSet(true)) {
      completePromise(cleanFuture, () -> function.clean(config, context));
    }
    return cleanFuture;
  }

  CompletableFuture<?> run(Object ignored) {
    if (!runStarted.getAndSet(true)) {
      return completePromise(runFuture, () -> function.run(config, context))
              .thenRun(() -> {
                if (!context.isDryRun()) context.say("DONE:", displayName());
              });
    }
    return runFuture;
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
    String cleanStatus = describeStatus(context.activePhases().doClean, cleanStarted.get(), cleanFuture);
    String runStatus = describeStatus(context.activePhases().doRun, runStarted.get(), runFuture);
    return String.format("%s\n cln:%s \n run:%s ", targetInstanceId(), cleanStatus, runStatus);
  }

  private String describeStatus(boolean enabled, boolean started, Promise<Nothing> future) {
    return BooleanChoice.of(!enabled, SKIPPED_STATUS)
            .or(!started, " (waiting)")
            .or(!future.isDone(), PENDING_STATUS)
            .or(!future.isCompletedExceptionally(), DONE_STATUS)
            .or(future.isCancelled(), ABORTED_STATUS)
            .otherwise(FAILED_STATUS);
  }
}
