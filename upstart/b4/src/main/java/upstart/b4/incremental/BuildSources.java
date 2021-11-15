package upstart.b4.incremental;

import upstart.util.exceptions.UncheckedIO;
import org.immutables.value.Value;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.attribute.FileTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.stream.Stream;

@FunctionalInterface
public interface BuildSources {
  static BuildSources of(Path... paths) {
    return of(Arrays.asList(paths));
  }

  static BuildSources of(Collection<Path> paths) {
    return () -> paths.stream().map(PathInfo::of);
  }

  Stream<PathInfo> paths() throws IOException;

  default Stream<PathInfo> filesAsNewAs(FileTime newerThan) {
    return UncheckedIO.getUnchecked(() -> paths().filter(pathInfo -> pathInfo.isRegularFile() && pathInfo.isAsNewAs(newerThan)));
  }

  default BuildSources merge(BuildSources other) {
    return () -> Stream.concat(paths(), other.paths());
  }

  default boolean hasNewerFile(FileTime newerThan) {
    try (Stream<PathInfo> pathStream = filesAsNewAs(newerThan)) {
      //        first.ifPresent(path -> UncheckedIO.runUnchecked(() -> System.out.println("OUT OF DATE FILE: " + path + ", " + Files.getLastModifiedTime(path) + " > " + newerThan)));
      return pathStream.findFirst().isPresent();
    }
  }

  @Value.Immutable
  @Value.Style(deepImmutablesDetection = true)
  interface GlobBuildSources extends BuildSources {
    static ImmutableGlobBuildSources.Builder builder() {
      return ImmutableGlobBuildSources.builder();
    }

    Path root();
    Glob glob();

    FileSystem fileSystem();


//    @Value.Lazy
//    default FileTime newestModificationTime() {
//      PathMatcher pathMatcher = pathMatcher();
//      class YoungestTimestampWalker extends SimpleFileVisitor<Path> {
//        private Optional<FileTime> newest = Optional.empty();
//
//        private FileTime walk() {
//          UncheckedIO.runUnchecked(() -> Files.walkFileTree(root(), this));
//          return newest.orElseThrow(() -> new IllegalStateException("No files found "));
//        }
//
//        @Override
//        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
//          if (attrs.isRegularFile() && pathMatcher.matches(file)) {
//            FileTime modification = attrs.lastModifiedTime();
//            if (!newest.filter(t -> t.compareTo(modification) >= 0).isPresent()) {
//              newest = Optional.of(modification);
//            }
//          }
//          return FileVisitResult.CONTINUE;
//        }
//      }
//
//      return new YoungestTimestampWalker().walk();
//    }

    @Value.Lazy
    default PathMatcher pathMatcher() {
      return glob().pathMatcher();
    }

    @Override
    default Stream<PathInfo> paths() throws IOException {
      return Files.walk(root())
              .filter(pathMatcher()::matches)
              .map(PathInfo::of);
    }
  }
}
