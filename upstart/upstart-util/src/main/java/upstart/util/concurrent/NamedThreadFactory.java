package upstart.util.concurrent;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public class NamedThreadFactory implements ThreadFactory {
  private static final LoadingCache<String, AtomicInteger> THREAD_NAME_COUNTERS = CacheBuilder.newBuilder().build(new CacheLoader<>() {
    @Override
    public AtomicInteger load(String key) {
      return new AtomicInteger();
    }
  });
  private final String threadNamePrefix;
  private final AtomicInteger nameCounter;

  public NamedThreadFactory(String threadNamePrefix) {
    this.threadNamePrefix = threadNamePrefix;
    nameCounter = THREAD_NAME_COUNTERS.getUnchecked(threadNamePrefix);
  }

  public ThreadFactory daemonize() {
    return new Daemonized();
  }

  @Override
  public Thread newThread(Runnable task) {
    return new Thread(task, threadNamePrefix + "-" + nameCounter.incrementAndGet());
  }

  class Daemonized implements ThreadFactory {

    @Override
    public Thread newThread(Runnable r) {
      Thread thread = NamedThreadFactory.this.newThread(r);
      thread.setDaemon(true);
      return thread;
    }
  }
}
