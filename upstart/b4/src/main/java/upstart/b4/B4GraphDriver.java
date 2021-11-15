package upstart.b4;

import com.google.common.base.Functions;
import com.google.common.util.concurrent.MoreExecutors;
import upstart.services.ExecutionThreadService;
import upstart.services.ThreadPoolService;
import upstart.util.concurrent.CompletableFutures;
import upstart.util.concurrent.Promise;
import picocli.CommandLine;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

@Singleton
public class B4GraphDriver extends ExecutionThreadService {
  private final TargetInvocationGraph invocationGraph;
  private final Map<TargetInstanceId, TargetRunner> targetRunners;
  private final ThreadPoolService threadPool;
  private final AtomicBoolean canceled = new AtomicBoolean();
  private final AtomicReference<Throwable> failure = new AtomicReference<>();
  private final Promise<Throwable> failureFuture = new Promise<>();

  @Inject
  B4GraphDriver(
          TargetInvocationGraph invocationGraph,
          Map<TargetInstanceId, TargetRunner> targetRunners,
          B4Module.ThreadPool threadPool
  ) {
    this.invocationGraph = invocationGraph;
    this.targetRunners = targetRunners;
    this.threadPool = threadPool;
  }

  public CompletableFuture<Throwable> failureFuture() {
    return failureFuture;
  }

  public Optional<Throwable> failure() {
    return Optional.ofNullable(failure.get());
  }

  @Override
  protected void run() {
    targetRunners.values().forEach(runner -> {
      cancelOnFailure(runner.cleanFuture());
      cancelOnFailure(runner.runFuture());
    });

    Function<TargetInvocation, TargetRunner> getRunner = Functions.forMap(targetRunners).compose(TargetInvocation::id);

    CompletableFuture<Void> future = CompletableFutures.allOf(
            invocationGraph.allInvocations()
                    .filter(TargetExecutionConfig::doClean)
                    .map(targetInvocation -> {
                      TargetRunner runner = getRunner.apply(targetInvocation);
                      return CompletableFutures.allOf(invocationGraph.successors(targetInvocation)
                              .filter(TargetExecutionConfig::doClean)
                              .map(getRunner)
                              .map(TargetRunner::cleanFuture)
                      ).thenComposeAsync(runner::clean, executorFor(runner));
                    })
    ).thenCompose(ignored -> CompletableFutures.allOf(
            invocationGraph.allInvocations()
                    .filter(TargetExecutionConfig::doRun)
                    .map(targetInvocation -> {
                      TargetRunner runner = getRunner.apply(targetInvocation);
                      return CompletableFutures.allOf(invocationGraph.predecessors(targetInvocation)
                              .filter(TargetExecutionConfig::doRun)
                              .map(getRunner)
                              .map(TargetRunner::runFuture)
                      ).thenComposeAsync(runner::run, executorFor(runner));
                    })
            )
    );
    CompletableFutures.recover(future, Exception.class, e -> null).join();
    failure().ifPresent(failureFuture::complete);
  }

  @Override
  protected void triggerShutdown() {
    B4.WARN_LOG.warn("Interrupted, triggering shutdown...");
    cancel();
  }

  private <T> CompletableFuture<T> cancelOnFailure(CompletableFuture<T> future) {
    return future.whenComplete((ignored, e) -> {
      if (e != null) {
        if (failure.compareAndSet(null, e)) {
          cancel();
        } else if (!B4CancellationException.isCancellation(e)) {
          boolean retry = false;
          do {
            Throwable prev = failure.get();
            if (B4CancellationException.isCancellation(prev)) {
              retry = !failure.compareAndSet(prev, e);
            } else {
              prev.addSuppressed(e);
            }
          } while (retry);
        }
      }
    });
  }

  private void cancel() {
    if (canceled.compareAndSet(false, true)) {
      B4CancellationException cancellationException = new B4CancellationException();
      for (TargetRunner targetRunner : targetRunners.values()) {
        targetRunner.cancel(cancellationException);
      }
    }
  }

  private Executor executorFor(TargetRunner targetExecutor) {
    return targetExecutor.runOnSeparateThread() ? threadPool : MoreExecutors.directExecutor();
  }

  @Override
  public String toString() {
    return super.toString() + "\n" + B4Cli.renderHighlightPlaceholders(renderStatusGraph(), CommandLine.Help.Ansi.OFF);
  }

  public String renderStatusGraph() {
    return invocationGraph.render(invocation -> targetRunners.get(invocation.id()).toString());
  }
}
