package upstart.config.dynamic;

import com.sun.nio.file.SensitivityWatchEventModifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import upstart.util.annotations.Tuple;
import upstart.util.collect.MoreCollectors;
import upstart.util.concurrent.CompletableFutures;
import upstart.util.concurrent.SimpleReference;
import upstart.util.concurrent.services.ExecutionThreadService;
import upstart.util.exceptions.UncheckedIO;
import upstart.util.functions.AsyncFunction;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.time.Instant;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static com.google.common.base.Preconditions.checkState;

@Singleton
public class JdkFileModificationWatchService extends ExecutionThreadService implements FileModificationWatchService {
  private static final Logger LOG = LoggerFactory.getLogger(JdkFileModificationWatchService.class);
  private final FileSystem fileSystem;
  private final FileModificationWatchServiceConfig config;
  private final Set<Watcher> watchedFiles = new HashSet<>();
  private Map<Path, Watcher> fileWatchers;
  private WatchService watcher;

  @Inject
  public JdkFileModificationWatchService(
          FileSystem fileSystem,
          FileModificationWatchServiceConfig config
  ) {
    this.fileSystem = fileSystem;
    this.config = config;
  }

  @Override
  protected synchronized void startUp() throws Exception {
    watcher = fileSystem.newWatchService();
    fileWatchers = watchedFiles.stream().collect(MoreCollectors.toImmutableIndexMap(Watcher::path));
    fileWatchers.keySet().stream()
            .map(path -> path.toAbsolutePath().getParent())
            .distinct()
            .forEach(UncheckedIO.consumer(dir -> {
              dir.register(
                      watcher,
                      new WatchEvent.Kind[] {StandardWatchEventKinds.ENTRY_CREATE,
                              StandardWatchEventKinds.ENTRY_MODIFY,
                              StandardWatchEventKinds.ENTRY_DELETE},
                      SensitivityWatchEventModifier.HIGH // kludge to ensure that updates don't take 10s+ to be noticed on MacOS
              );
            }));

    CompletableFutures.allOf(fileWatchers.values().stream().map(Watcher::publish)).join();
  }

  @Override
  protected void run() throws Exception {
    while (isRunning()) {
      WatchKey watchKey = watcher.poll(config.shutdownPollMillis(), TimeUnit.MILLISECONDS);
      if (watchKey != null) {
        Path dir = (Path) watchKey.watchable();
        CompletableFutures.allOf(
                watchKey.pollEvents().stream()
                        .filter(event -> !event.kind().equals(StandardWatchEventKinds.OVERFLOW))
                        .flatMap(event -> Optional.ofNullable(fileWatchers.get(dir.resolve((Path) event.context()))).stream())
                        .distinct()
                        .map(Watcher::notifyModification)
        ).join();
      }
    }
  }

  @Override
  public void watch(Path path, AsyncFunction<? super Path, Void> callback) {
    initialize(() -> watchedFiles.add(Watcher.of(path, callback.withSafeWrapper())));
  }

  @Override
  public synchronized void initialize(Runnable runnable) {
    checkState(state() == State.NEW, "Cannot watch a config file after the service has started");
    runnable.run();
  }

  @Tuple
  interface Watcher {
    static Watcher of(Path path, AsyncFunction<? super Path, Void> callback) {
      return ImmutableWatcher.of(path, new SimpleReference<>(Instant.EPOCH), callback);
    }

    Path path();

    SimpleReference<Instant> modificationTime();
    AsyncFunction<? super Path, Void> callback();

    default CompletableFuture<Void> notifyModification() {
      LOG.info("Triggering watched file update: {}", path());
      return publish();
    }

    default CompletableFuture<Void> publish() {
      return callback().apply(path()).toCompletableFuture();
    }
  }
}
