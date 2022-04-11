package upstart.b4;

import com.google.common.base.Functions;
import com.google.common.base.Stopwatch;
import com.google.common.util.concurrent.MoreExecutors;
import upstart.util.concurrent.services.ExecutionThreadService;
import upstart.util.concurrent.services.ThreadPoolService;
import upstart.util.concurrent.CompletableFutures;
import upstart.util.concurrent.Promise;
import picocli.CommandLine;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Duration;
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
  private final B4Console console;
  private final B4Application app;
  private final AtomicBoolean canceled = new AtomicBoolean();
  private final AtomicReference<Throwable> failure = new AtomicReference<>();
  private final Promise<Throwable> failureFuture = new Promise<>();
  private final boolean statusLogEnabled;

  @Inject
  B4GraphDriver(
          TargetInvocationGraph invocationGraph,
          Map<TargetInstanceId, TargetRunner> targetRunners,
          B4Module.ThreadPool threadPool,
          B4Console console,
          B4Application app
  ) {
    this.invocationGraph = invocationGraph;
    this.targetRunners = targetRunners;
    this.threadPool = threadPool;
    this.console = console;
    this.app = app;
    B4Function.Verbosity verbosity = app.baseExecutionConfig().effectiveVerbosity();
    statusLogEnabled = verbosity.logCommands && !verbosity.logOutput;
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

    Stopwatch stopwatch = Stopwatch.createStarted();
    logStatus("Starting...", stopwatch);
    CompletableFuture<Void> completionFuture = CompletableFutures.recover(CompletableFutures.allOf(
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
    ), Exception.class, e -> null);

    if (statusLogEnabled) {
      try {
        while (!CompletableFutures.isDoneWithin(Duration.ofSeconds(8), completionFuture)) {
          logStatus("In progress...", stopwatch);
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
    completionFuture.join();
    logStatus("Completed", stopwatch);
    failure().ifPresent(failureFuture::complete);
  }

  private void logStatus(String message, Stopwatch stopwatch) {
    if (app.baseExecutionConfig().dryRun()) message += B4TaskContext.DRYRUN_LOG_MARKER;
    if (statusLogEnabled) B4.WARN_LOG.info(message + "{}\n(after {})", console.renderHighlightPlaceholders(renderStatusGraph()), stopwatch);
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
          while (true) {
            Throwable prev = failure.get();
            if (B4CancellationException.isCancellation(prev)) {
              if (failure.compareAndSet(prev, e)) {
                break;
              }
            } else {
              prev.addSuppressed(e);
              break;
            }
          }
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
    return super.toString() + "\n" + B4Console.renderHighlightPlaceholders(renderStatusGraph(), CommandLine.Help.Ansi.OFF);
  }

  public String renderStatusGraph() {
    return invocationGraph.render(invocation -> targetRunners.get(invocation.id()).toString());
  }
}
