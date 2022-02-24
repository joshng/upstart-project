package upstart.b4.incremental;

import upstart.util.annotations.Tuple;
import org.immutables.value.Value;

import java.nio.file.Path;

@Value.Immutable
@Tuple
public interface FileBuildDependency extends BuildDependency {
  static BuildDependency of(Path source, Path target) {
    return of(BuildSources.of(source), BuildTargets.of(target));
  }

  static BuildDependency of(BuildSources sources, BuildTargets targets) {
    return ImmutableFileBuildDependency.of(sources, targets);
  }

  BuildSources sources();
  BuildTargets targets();

  @Override
  @Value.Lazy
  default boolean requiresRebuild() {
    return targets().requiresRebuild(sources());
  }
}
