package upstart.b4.incremental;

import upstart.util.collect.PairStream;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.function.Function;
import java.util.stream.Stream;

public interface BuildDependency {
  static BuildDependency structural(BuildSources sources, Function<Path, Stream<Path>> pathTransformer) {
    try (Stream<PathInfo> sourceInfo = sources.paths()) {
      return PairStream.withMappedValues(sourceInfo
                      .filter(PathInfo::isRegularFile)
                      .map(PathInfo::path),
              pathTransformer
      ).map((source, targets) -> FileBuildDependency.of(BuildSources.of(source), BuildTargets.of(targets)))
              .reduce(BuildDependency::merge)
              .orElseThrow(() -> new IllegalArgumentException("No sources found"));

    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  boolean requiresRebuild();

  default BuildDependency merge(BuildDependency other) {
    return () -> requiresRebuild() || other.requiresRebuild();
  }

}
