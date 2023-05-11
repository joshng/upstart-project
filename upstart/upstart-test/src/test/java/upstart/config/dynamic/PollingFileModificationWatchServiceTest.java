package upstart.config.dynamic;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import upstart.config.EnvironmentConfig;
import upstart.config.UpstartModule;
import upstart.config.annotations.ConfigPath;
import upstart.test.FakeFileSystemTest;
import upstart.test.FakeTime;
import upstart.test.FakeTimeTest;
import upstart.test.UpstartLibraryTest;
import upstart.test.UpstartServiceTest;
import upstart.util.concurrent.Promise;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static com.google.common.truth.Truth.assertThat;

@UpstartServiceTest
@UpstartLibraryTest
@EnvironmentConfig.Fixture("""
        upstart.file-modification-service {
          usePolling: true
          pollingInterval: 1s
        }
        upstart.test.file-watcher {
          watchedFile: /test-file
        }
        """)
@FakeFileSystemTest
@FakeTimeTest(interceptSchedules = PollingFileModificationWatchService.class)
class PollingFileModificationWatchServiceTest extends UpstartModule {
  @Override
  protected void configure() {
    install(new FileModificationWatchService.WatchServiceModule());
    bind(FakeFileWatcher.class).asEagerSingleton();
    bindConfig(FakeWatchConfig.class);
  }

  @BeforeEach
  void createFile(FileSystem fs) throws Exception {
    Files.writeString(fs.getPath("/test-file"), "test1");
  }

  @Inject FakeFileWatcher fileWatcher;

  @Test
  void fileModificationIsObserved(FakeTime time, FileSystem fs) throws IOException {
    time.runPendingJobs();
    assertThat(fileWatcher.observedContents).containsExactly("test1");
    time.advance(Duration.ofSeconds(5));
    assertThat(fileWatcher.observedContents).containsExactly("test1");
    Files.writeString(fs.getPath("/test-file"), "test2");
    assertThat(fileWatcher.observedContents).containsExactly("test1");
    time.advance(Duration.ofSeconds(1));
    assertThat(fileWatcher.observedContents).containsExactly("test1", "test2");
  }

  @Singleton
  static class FakeFileWatcher {
    final List<String> observedContents = new ArrayList<>();
    @Inject
    public FakeFileWatcher(FileModificationWatchService watchService, FakeWatchConfig config) {
      watchService.watch(config.watchedFile(), path -> Promise.thatCompletes(p -> {
        observedContents.add(Files.readString(path));
        p.complete(null);
      }));
    }
  }

  @ConfigPath("upstart.test.file-watcher")
  interface FakeWatchConfig {
    Path watchedFile();
  }
}
