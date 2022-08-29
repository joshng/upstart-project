package upstart.cli;

import picocli.CommandLine;
import upstart.UpstartDeploymentStage;

import java.nio.file.Path;
import java.util.Optional;

public abstract class UpstartSubCommand<P extends UpstartContextOptions> extends UpstartCommand {
  @CommandLine.ParentCommand public P parent;

  @Override
  protected Optional<Path> upstartConfigFile() {
    return Optional.ofNullable(parent.configFile);
  }

  @Override
  protected UpstartDeploymentStage upstartDeploymentStage() {
    return parent.deploymentStage;
  }

  @Override
  protected String upstartEnvironment() {
    return parent.upstartEnv;
  }
}
