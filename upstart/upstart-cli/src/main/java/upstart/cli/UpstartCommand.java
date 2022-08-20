package upstart.cli;

import picocli.CommandLine;
import upstart.UpstartApplication;
import upstart.UpstartDeploymentStage;
import upstart.UpstartService;
import upstart.config.UpstartEnvironment;
import upstart.util.concurrent.services.ServiceSupervisor;
import upstart.util.exceptions.ThrowingRunnable;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;

public abstract class UpstartCommand extends UpstartApplication implements ThrowingRunnable {
  @CommandLine.Spec private CommandLine.Model.CommandSpec spec;

  public void executeMain(String... args) {
    new CommandLine(this).execute(args);
  }

  @Override
  public final void runOrThrow() throws Exception {
    System.setProperty(UpstartEnvironment.UPSTART_ENVIRONMENT, upstartEnvironment());
    System.setProperty(UpstartEnvironment.UPSTART_DEPLOYMENT_STAGE, upstartDeploymentStage().toString());
    upstartConfigFile().ifPresent(configFile -> System.setProperty(UpstartEnvironment.UPSTART_DEV_CONFIG, configFile.toString()));

    CommandLine rootCommand = spec.commandLine();
    while (rootCommand.getParent() != null) rootCommand = rootCommand.getParent();
    System.setProperty("upstart.context.application", rootCommand.getCommandName());

    UpstartService upstartService = buildServiceSupervisor()
            .start()
            .supervisedService();

    execute(upstartService);
  }

  protected abstract UpstartDeploymentStage upstartDeploymentStage();

  protected abstract String upstartEnvironment();

  protected Optional<Path> upstartConfigFile() {
    return UpstartEnvironment.findDevConfigPath();
  }

  @Override
  public ServiceSupervisor.BuildFinal<UpstartService> configureSupervisor(ServiceSupervisor.ShutdownConfigStage<UpstartService> builder) {
    return builder.shutdownGracePeriod(shutdownGracePeriod());
  }

  protected abstract void execute(UpstartService service) throws Exception;

  protected Duration shutdownGracePeriod() {
    return Duration.ofMillis(500);
  }
}
