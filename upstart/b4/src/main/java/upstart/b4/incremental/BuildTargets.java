package upstart.b4.incremental;

import org.immutables.value.Value;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Set;
import java.util.stream.Stream;

@Value.Immutable
public interface BuildTargets {
  static BuildTargets of(Path... paths) {
    return of(Stream.of(paths));
  }

  static BuildTargets of(Stream<Path> paths) {
    return builder().addAllPaths(paths::iterator).build();
  }


  static ImmutableBuildTargets.Builder builder() {
    return ImmutableBuildTargets.builder();
  }

  Set<Path> paths();

  default boolean requiresRebuild(BuildSources source) {
    return source.hasNewerFile(eldestModificationTime());
  }

  @Value.Lazy
  default FileTime eldestModificationTime() {
    if (paths().isEmpty()) return FileScanner.MIN_FILE_TIME;

    FileTime min = FileScanner.MAX_FILE_TIME;
    for (Path path : paths()) {
      if (!Files.exists(path)) return FileScanner.MIN_FILE_TIME;
      FileTime lastModifiedTime = FileScanner.lastModifiedTime(path);
      if (min.compareTo(lastModifiedTime) > 0) {
        min = lastModifiedTime;
      }
    }

    return min;
  }
}
