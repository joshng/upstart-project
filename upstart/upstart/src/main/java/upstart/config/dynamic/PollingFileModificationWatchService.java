package upstart.config.dynamic;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import upstart.util.collect.MoreCollectors;
import upstart.util.concurrent.CompletableFutures;
import upstart.util.concurrent.Deadline;
import upstart.util.concurrent.Promise;
import upstart.util.concurrent.services.ScheduledService;
import upstart.util.functions.AsyncFunction;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static com.google.common.base.Preconditions.checkState;

@Singleton
public class PollingFileModificationWatchService extends ScheduledService implements FileModificationWatchService {
  private static final Logger LOG = LoggerFactory.getLogger(PollingFileModificationWatchService.class);
  private final FileModificationWatchServiceConfig config;
  private final Set<Watcher> watchedFiles = new HashSet<>();
  private Map<Path, Watcher> fileWatchers;

  @Inject
  public PollingFileModificationWatchService(FileModificationWatchServiceConfig config) {
    this.config = config;
  }

  @Override
  protected synchronized void startUp() throws Exception {
    fileWatchers = watchedFiles.stream().collect(MoreCollectors.toImmutableIndexMap(Watcher::path));
    poll().join();
  }

  @Override
  protected Schedule schedule() {
    return Schedule.fixedRate(Duration.ZERO, config.pollInterval().orElseThrow());
  }

  @Override
  protected void runOneIteration() throws InterruptedException {
    Deadline shutdownPollDeadline = Deadline.within(config.shutdownPollInterval());
    Promise<Void> promise = poll();
    while (isRunning() && !shutdownPollDeadline.awaitDone(promise)) {
      shutdownPollDeadline = Deadline.within(config.shutdownPollInterval());
    }
    promise.getNow(null);
  }

  private Promise<Void> poll() {
    return Promise.allOf(
            fileWatchers.values().stream()
                    .map(Watcher::poll)
    );
  }

  @Override
  public void watch(Path path, AsyncFunction<? super Path, Void> callback) {
    initialize(() -> watchedFiles.add(new Watcher(path, callback.withSafeWrapper())));
  }

  @Override
  public synchronized void initialize(Runnable runnable) {
    checkState(state() == State.NEW, "Cannot watch a config file after the service has started");
    runnable.run();
  }

  //  @Tuple
  record Watcher(Path path, AtomicReference<Instant> lastModified, AsyncFunction<? super Path, Void> callback) {
    public Watcher(Path path, AsyncFunction<? super Path, Void> callback) {
      this(path, new AtomicReference<>(Instant.EPOCH), callback);
    }

    CompletableFuture<Void> publish() {
      return callback.apply(path).toCompletableFuture();
    }

    CompletableFuture<Void> poll() {
      return CompletableFutures.callSafely(() -> {
        Instant currentModified = Files.getLastModifiedTime(path).toInstant();
        if (!currentModified.equals(lastModified.get())) {
          LOG.warn("Triggering watched file update: {}", path);
          lastModified.set(currentModified);
          return publish();
        } else {
          return CompletableFutures.nullFuture();
        }
      });
    }
  }
}
