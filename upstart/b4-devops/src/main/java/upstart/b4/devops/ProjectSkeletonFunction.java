package upstart.b4.devops;

import com.google.common.io.MoreFiles;
import com.google.common.io.RecursiveDeleteOption;
import org.immutables.value.Value;
import upstart.b4.B4Function;
import upstart.b4.B4TaskContext;
import upstart.config.annotations.DeserializedImmutable;
import upstart.util.MoreFunctions;
import upstart.util.Patterns;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;

public class ProjectSkeletonFunction implements B4Function<ProjectSkeletonFunction.SkeletonConfig> {
  @Override
  public boolean alwaysCleanBeforeRun() {
    return true;
  }

  @Override
  public void clean(SkeletonConfig config, B4TaskContext context) throws Exception {
    if (Files.exists(config.skeletonDir())) {
      context.effect("Cleaning project-skeleton", config.skeletonDir().toString())
              .run(() -> MoreFiles.deleteRecursively(config.skeletonDir(), RecursiveDeleteOption.ALLOW_INSECURE));
    }
  }

  @Override
  public void run(SkeletonConfig config, B4TaskContext context) throws Exception {
    context.effect("building skeleton maven project")
            .run(() -> config.buildSkeleton(context));
  }

  @Override
  public void cancel() {

  }

  @DeserializedImmutable
  public interface SkeletonConfig {
    Path sourceDir();
    Path skeletonDir();

    Set<Pattern> preservedPatterns();
    Set<Path> preservedFiles();

    @Value.Derived
    @Value.Auxiliary
    default Pattern preservedPattern() {
      return Patterns.anyOf(preservedPatterns());
    }

    default void buildSkeleton(B4TaskContext context) throws IOException {
      Predicate<Path> preservePredicate = MoreFunctions.onResultOf(path -> path.getFileName().toString(), preservedPattern().asPredicate());

      // need to save these sources to a list rather than copy them by iterating the `walk` stream,
      // because otherwise the copies themselves can end up in the sources.
      List<Path> sources = Files.walk(sourceDir()).filter(preservePredicate).toList();
      for (Path path : sources) {
        copy(path, context);
      }

      for (Path path : preservedFiles()) {
        copy(path, context);
      }
    }
    default void copy(Path projectPath, B4TaskContext context) throws IOException {
      Path skeletonFile = skeletonDir().resolve(sourceDir().relativize(projectPath));
      Path target = skeletonFile.getParent().relativize(projectPath);
      context.effect("Linking to project skeleton:", projectPath.toString())
              .run(() -> {
                Files.createDirectories(skeletonFile.getParent());
                Files.createLink(skeletonFile, projectPath);
//                Files.createSymbolicLink(skeletonFile, target);
//                Files.copy(projectPath, skeletonFile);
              });
    }
  }
}
