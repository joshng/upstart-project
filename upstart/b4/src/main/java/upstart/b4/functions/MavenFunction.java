package upstart.b4.functions;

import org.immutables.value.Value;
import upstart.b4.B4Function;
import upstart.b4.B4TaskContext;
import upstart.b4.TargetInvocation;
import upstart.config.annotations.DeserializedImmutable;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class MavenFunction implements B4Function<MavenFunction.MavenBuildConfig> {

  @Override
  public void clean(MavenBuildConfig config, B4TaskContext context) throws Exception {
    invoke(TargetInvocation.Phases.CleanOnly, config, context);
  }

  @Override
  public void run(MavenBuildConfig config, B4TaskContext context) throws Exception {
    invoke(TargetInvocation.Phases.DirtyRun, config, context);
  }

  private void invoke(TargetInvocation.Phases phase, MavenBuildConfig config, B4TaskContext context) {
    context.effectCommand(config.mavenExecutable(), builder -> builder.addAllArgs(config.fullMavenCommand(phase)));
  }

  @Override
  public void cancel() {
  }

  @DeserializedImmutable
  interface MavenBuildConfig {
    String DEFAULT_GOAL = "package";

    Optional<Path> pomFile();
    boolean skipTests();
    String mavenExecutable();
    Set<String> goals();
    Set<String> projects();
    Set<String> profiles();
    boolean buildAllProjects();
    Set<String> extraArgs();
    double threadsPerCore();

    @Value.Default
    default List<String> fullMavenCommand(TargetInvocation.Phases phases) {
      List<String> args = new ArrayList<>();
      if (skipTests()) args.add("-DskipTests");
      if (phases.doClean) {
        args.add("clean");
      }
      pomFile().ifPresent(pomFile -> args.addAll(Arrays.asList("--file", pomFile.toString())));

      if (phases.doRun) {
        if (goals().isEmpty()) {
          args.add(DEFAULT_GOAL);
        } else {
          args.addAll(goals());
        }
      }

      if (!profiles().isEmpty()) {
        args.add("-P" + String.join(",", profiles()));
      }
      if (!buildAllProjects() && !projects().isEmpty()) {
        args.add("-pl");
        args.add(String.join(",", projects()));
        args.add("-am");
      }
      if (threadsPerCore() > 0) args.add("-T" + threadsPerCore() + "C");
      args.addAll(extraArgs());
      return args;
    }
  }
}
