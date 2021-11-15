package upstart.util.concurrent;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;

public class BlockingExecutionHandler implements RejectedExecutionHandler {
  private final BlockingQueue<Runnable> queue;
  private final RejectedExecutionHandler listener;

  public BlockingExecutionHandler(ThreadPoolExecutor executor) {
    this(executor, (r, t) -> {});
  }
  public BlockingExecutionHandler(ThreadPoolExecutor executor, RejectedExecutionHandler listener) {
    this(executor.getQueue(), listener);
  }

  public BlockingExecutionHandler(BlockingQueue<Runnable> queue, RejectedExecutionHandler listener) {
    this.queue = queue;
    this.listener = listener;
  }

  @Override
  public void rejectedExecution(Runnable runnable, ThreadPoolExecutor executor) {
    try {
      listener.rejectedExecution(runnable, executor);
      queue.put(runnable);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RejectedExecutionException("Interrupted while waiting to submit", e);
    }
  }
}
