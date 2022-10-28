package upstart.cli;

import picocli.CommandLine;
import upstart.UpstartDeploymentStage;
import upstart.config.UpstartEnvironment;
import upstart.config.UpstartModule;

import java.nio.file.Path;

public class UpstartContextOptions extends UpstartModule {
  @CommandLine.Option(
          names = {"--upstart-config"},
          description = "Upstart config-file (optional)",
          scope = CommandLine.ScopeType.INHERIT,
          showDefaultValue = CommandLine.Help.Visibility.ALWAYS
  )
  public Path configFile = UpstartEnvironment.findDevConfigPath().orElse(null);

  @CommandLine.Option(
          names = "--upstart-env",
          description = "Upstart environment",
          defaultValue = "dev",
          scope = CommandLine.ScopeType.INHERIT,
          showDefaultValue = CommandLine.Help.Visibility.ALWAYS,
          order = Integer.MAX_VALUE - 1
  )
  public String upstartEnv;

  @CommandLine.Option(
          names = "--upstart-stage",
          description = "Upstart deployment stage (${COMPLETION-CANDIDATES})",
          defaultValue = "dev",
          scope = CommandLine.ScopeType.INHERIT,
          showDefaultValue = CommandLine.Help.Visibility.ALWAYS,
          order = Integer.MAX_VALUE
  )
  public UpstartDeploymentStage deploymentStage = UpstartDeploymentStage.dev;
}
