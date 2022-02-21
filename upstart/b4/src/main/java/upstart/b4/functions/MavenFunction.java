package upstart.b4.functions;

import upstart.b4.B4Function;
import upstart.b4.B4TaskContext;
import upstart.b4.TargetInvocation;
import org.immutables.value.Value;
import upstart.config.annotations.DeserializedImmutable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
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

    boolean skipTests();
    String mavenExecutable();
    Set<String> goals();
    Set<String> projects();
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
      if (phases.doRun) {
        if (goals().isEmpty()) {
          args.add(DEFAULT_GOAL);
        } else {
          args.addAll(goals());
        }
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
