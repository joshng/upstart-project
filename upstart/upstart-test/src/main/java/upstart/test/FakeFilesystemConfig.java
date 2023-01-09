package upstart.test;

import org.immutables.value.Value;

import java.time.Duration;
import java.util.Optional;

@Value.Immutable
public interface FakeFilesystemConfig {
  static ImmutableFakeFilesystemConfig.Builder builder() {
    return ImmutableFakeFilesystemConfig.builder();
  }

  Optional<String> workingDirectory();

  Optional<Duration> watchServicePollInterval();

  interface Builder {
    ImmutableFakeFilesystemConfig.Builder workingDirectory(String workDir);

    default ImmutableFakeFilesystemConfig.Builder withRealCwd() {
      return workingDirectory(System.getProperty("user.dir"));
    }
  }
}
