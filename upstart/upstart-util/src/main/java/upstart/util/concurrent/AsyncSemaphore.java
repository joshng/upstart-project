package upstart.util.concurrent;

import java.util.Queue;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;

public class AsyncSemaphore {
  private final int maxConcurrentTasks;
  private final AtomicLong activeTasks = new AtomicLong();
  private final Queue<Runnable> tasks = new ConcurrentLinkedQueue<>();
  private final Executor executor = new SameThreadTrampolineExecutor();

  public AsyncSemaphore(int maxConcurrentTasks) {
    this.maxConcurrentTasks = maxConcurrentTasks;
  }

  public <T> Promise<T> submit(Callable<? extends CompletionStage<T>> task) {
    return Promise.thatCompletes(promise -> {
      tasks.offer(() -> promise.tryCompleteWith(task)
              .whenComplete((ignored, e) -> {
                if (activeTasks.decrementAndGet() >= maxConcurrentTasks) {
                  executor.execute(tasks.poll());
                }
              }));

      if (activeTasks.incrementAndGet() <= maxConcurrentTasks) {
        executor.execute(tasks.poll());
      }
    });
  }
}
