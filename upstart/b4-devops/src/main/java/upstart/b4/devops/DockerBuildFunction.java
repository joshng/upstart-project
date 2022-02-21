package upstart.b4.devops;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import upstart.b4.B4Function;
import upstart.b4.B4TaskContext;
import org.immutables.value.Value;

import java.nio.file.Path;
import java.util.Map;

public class DockerBuildFunction implements B4Function<DockerBuildFunction.DockerConfig> {
  @Override
  public void clean(DockerConfig config, B4TaskContext context) throws Exception {
    // TODO: should we support deleting docker-images here?
  }

  @Override
  public void run(DockerConfig config, B4TaskContext context) throws Exception {
    config.runCommand(context);
  }

  @Override
  public void cancel() {

  }

  @Value.Immutable
  @JsonDeserialize(builder = ImmutableDockerConfig.Builder.class)
  interface DockerConfig {
    String name();
    String tag();
    Path dockerAssemblyDir();
    String dockerExecutable();
    Map<String,String> dockerBuildArgs();

    default void runCommand(B4TaskContext context) {
      context.effectCommand(dockerExecutable(), builder -> {
        builder.workDir(dockerAssemblyDir())
          .addArgs("build", "-t", fullName(), ".");
        dockerBuildArgs().forEach((k, v) ->
          builder.addArgs("--build-arg", k + "=" + v));
	return builder;
      });
    }

    default String fullName() {
      return name() + ":" + tag();
    }
  }
}
