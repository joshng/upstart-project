package upstart.cli;

import picocli.CommandLine;
import upstart.UpstartDeploymentStage;
import upstart.UpstartService;

import java.nio.file.Path;
import java.util.Optional;

public abstract class UpstartSubCommand<P extends UpstartCommand> extends UpstartCommand {
  @CommandLine.ParentCommand public P parent;

  @Override
  protected Optional<Path> upstartConfigFile() {
    return parent.upstartConfigFile();
  }

  @Override
  protected UpstartDeploymentStage upstartDeploymentStage() {
    return parent.upstartDeploymentStage();
  }

  @Override
  protected String upstartEnvironment() {
    return parent.upstartEnvironment();
  }
}
