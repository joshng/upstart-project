package upstart.b4.incremental;

import upstart.util.exceptions.UncheckedIO;

import javax.inject.Inject;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class FileScanner {
  public static final FileTime MIN_FILE_TIME = FileTime.from(Instant.EPOCH);
  public static final FileTime MAX_FILE_TIME = FileTime.from(Instant.MAX);
  private final FileSystem fileSystem;

  @Inject
  public FileScanner(FileSystem fileSystem) {
    this.fileSystem = fileSystem;
  }

  public Path getPath(String path) {
    return fileSystem.getPath(path);
  }

  public BuildSources glob(Path root, Glob glob) {
    return glob(root, glob, fileSystem);
  }

  public BuildSources glob(Path root, Glob glob, FileSystem fileSystem) {
    return BuildSources.GlobBuildSources.builder()
            .glob(glob)
            .root(root)
            .fileSystem(fileSystem)
            .build();
  }

  public BuildSources sources(String... paths) {
    return sources(Stream.of(paths));
  }

  public BuildSources sources(Stream<String> paths) {
    return BuildSources.of(paths.map(fileSystem::getPath).collect(Collectors.toList()));
  }

  public BuildTargets target(Path... paths) {
    return BuildTargets.builder()
            .addPaths(paths)
            .build();
  }


  static FileTime lastModifiedTime(Path path) {
    return UncheckedIO.getUnchecked(() -> Files.getLastModifiedTime(path));
  }
}
