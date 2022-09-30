package upstart.cli;

import picocli.CommandLine;
import upstart.UpstartApplication;
import upstart.UpstartDeploymentStage;
import upstart.UpstartService;
import upstart.config.UpstartEnvironment;
import upstart.util.collect.MoreStreams;
import upstart.util.concurrent.services.ServiceSupervisor;
import upstart.util.exceptions.ThrowingRunnable;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.util.stream.Stream;

public abstract class UpstartCommand extends UpstartApplication {
  @CommandLine.Spec private CommandLine.Model.CommandSpec spec;

  public void executeMain(String... args) {
    new CommandLine(this).execute(args);
  }

  protected UpstartService startService() {
    CommandLine rootCommand = rootCommand();
    return startService(rootCommand.getCommandName());
  }

  protected UpstartService startService(String appName) {
    System.setProperty("upstart.context.application", appName);
    System.setProperty(UpstartEnvironment.UPSTART_ENVIRONMENT, upstartEnvironment());
    System.setProperty(UpstartEnvironment.UPSTART_DEPLOYMENT_STAGE, upstartDeploymentStage().toString());
    upstartConfigFile().ifPresent(configFile -> System.setProperty(UpstartEnvironment.UPSTART_DEV_CONFIG, configFile.toString()));

    UpstartService.Builder builder = builder();

    MoreStreams.filter(commandLineage().map(CommandLine::getCommand), com.google.inject.Module.class)
            .forEach(builder::installModule);

    return configureSupervisor(builder.buildServiceSupervisor()).start().supervisedService();
  }

  public CommandLine rootCommand() {
    CommandLine rootCommand = spec.commandLine();
    while (rootCommand.getParent() != null) rootCommand = rootCommand.getParent();
    return rootCommand;
  }

  Stream<CommandLine> commandLineage() {
    return MoreStreams.generate(spec.commandLine(), CommandLine::getParent);
  }

  protected abstract UpstartDeploymentStage upstartDeploymentStage();

  protected abstract String upstartEnvironment();

  protected abstract Optional<Path> upstartConfigFile();

    @Override
  public ServiceSupervisor.BuildFinal<UpstartService> configureSupervisor(ServiceSupervisor.ShutdownConfigStage<UpstartService> builder) {
    return builder.shutdownGracePeriod(shutdownGracePeriod());
  }

  protected Duration shutdownGracePeriod() {
    return Duration.ofMillis(500);
  }
}
