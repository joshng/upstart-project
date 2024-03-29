package upstart.cli;

import picocli.CommandLine;
import upstart.UpstartDeploymentStage;
import upstart.config.UpstartEnvironment;

import java.nio.file.Path;
import java.util.Optional;

public abstract class UpstartParentCommand extends UpstartCommand {
  @CommandLine.ArgGroup
  public UpstartContextOptions contextOptions;

  protected UpstartParentCommand() {
    contextOptions = contextOptions();
  }

  protected UpstartContextOptions contextOptions() {
    return new UpstartContextOptions();
  }

  @Override
  protected UpstartDeploymentStage upstartDeploymentStage() {
    return contextOptions.deploymentStage;
  }

  @Override
  protected String upstartEnvironment() {
    return contextOptions.upstartEnv;
  }

  @Override
  protected Optional<Path> upstartConfigFile() {
    return Optional.ofNullable(contextOptions.configFile).or(UpstartEnvironment::findDevConfigPath);
  }
}
