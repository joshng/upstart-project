package upstart.util.concurrent;

import upstart.util.Nothing;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Created by: josh 10/24/13 1:53 PM
 */
public class SameThreadTrampolineExecutor extends AbstractExecutorService {
  private final ThreadLocal<Trampoline> trampoline = new ThreadLocal<Trampoline>() {
    @Override
    protected Trampoline initialValue() {
      Trampoline t = new Trampoline();
      activeTrampolines.add(t);
      return t;
    }
  };
  private final AtomicBoolean shutdown = new AtomicBoolean(false);
  private final FutureCompletionTracker completionTracker = new FutureCompletionTracker();
  private final Set<Trampoline> activeTrampolines = ConcurrentHashMap.newKeySet();

  @Override
  public void execute(Runnable command) {
    submit(command);
  }

  public CompletableFuture<Nothing> submit(Runnable command) {
    return submit(Executors.callable(command, Nothing.NOTHING));
  }

  public <T> CompletableFuture<T> submit(Callable<T> command) {
    return completionTracker.track(trampoline.get().runWithTrampoline(new CompletableFutureTask<>(command)));
  }

  public <T> CompletableFuture<T> submitAsync(Callable<? extends CompletionStage<T>> command) {
    return CompletableFutures.sequence(submit(command));
  }

  @Override public void shutdown() {
    if (shutdown.compareAndSet(false, true)) completionTracker.setNoMoreJobs();
  }

  @Override public List<Runnable> shutdownNow() {
    shutdown();
    List<Runnable> cancelledJobs = new ArrayList<>();
    for (Trampoline activeTrampoline : activeTrampolines) {
      cancelledJobs.addAll(activeTrampoline.shutdown());
    }
    return cancelledJobs;
  }

  @Override public boolean isShutdown() {
    return shutdown.get();
  }

  @Override public boolean isTerminated() {
    return completionTracker.completionFuture().isDone();
  }

  @Override public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
    try {
      completionTracker.completionFuture().get(timeout, unit);
    } catch (ExecutionException e) {
      // ignore
    } catch (TimeoutException e) {
      return false;
    }
    return true;
  }

  private class Trampoline {
    private final Queue<CompletableFutureTask<?>> queue = new ConcurrentLinkedQueue<>();
    private boolean running = false;

    <T> CompletableFuture<T> runWithTrampoline(CompletableFutureTask<T> task) {
      if (isShutdown()) throw new RejectedExecutionException("ExecutorService was shutdown");
      queue.offer(task);
      if (!running) {
        running = true;
        try {
          drain(Runnable::run);
        } finally {
          trampoline.remove();
          activeTrampolines.remove(this);
        }
      }
      return task;
    }

    private void drain(Consumer<CompletableFutureTask<?>> operation) {
      CompletableFutureTask<?> job;
      while ((job = queue.poll()) != null) {
        operation.accept(job);
      }
    }

    public Collection<? extends Runnable> shutdown() {
      List<Runnable> cancelledTasks = new ArrayList<>();
      drain(task -> {
                if (task.cancel(false)) {
                  cancelledTasks.add(task);
                }
              });
      return cancelledTasks;
    }

  }

}
