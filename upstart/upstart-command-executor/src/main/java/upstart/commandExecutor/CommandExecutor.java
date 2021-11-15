package upstart.commandExecutor;

import upstart.util.LogLevel;
import org.slf4j.Logger;

import javax.inject.Inject;
import java.util.function.Function;

public class CommandExecutor {

  private final CommandExecutorSPI spi;

  @Inject
  public CommandExecutor(CommandExecutorSPI spi) {
    this.spi = spi;
  }

  public <R extends CommandResult> R run(String executable, Function<CommandSpecBuilder<CommandResult.ZeroExitCode>, CommandSpecBuilder<R>> builder) {
    return run(builder.apply(CommandSpec.builder(executable)).build());
  }

  public CommandResult.ZeroExitCode run(String executable, String... args) {
    return run(executable, b -> b.addArgs(args));
  }

  /**
   * Runs the command as specified with the given {@link CommandSpec}, and returns a {@link CommandResult} conveying
   * the outcome.
   * @see CommandResult
   */
  public <R extends CommandResult> R run(CommandSpec<R> commandSpec) {
    CommandResult externalResult;
    try {
      InternalCommandResult result = spi.execute(commandSpec);
      externalResult = InternalCommandResult.toExternal(commandSpec, result);
    } catch (CommandResult.CommandStartupException e) {
      externalResult = CommandResult.StartupFailed.builder()
              .command(commandSpec)
              .startupException(e)
              .build();
    } catch (CommandResult.CommandTimeoutException e) {
      externalResult = CommandResult.TimedOut.builder()
              .command(commandSpec)
              .timeoutException(e)
              .outputString(e.getOutputString())
              .errorString(e.getErrorString())
              .build();
    }

    return commandSpec.completionPolicy().apply(externalResult);
  }

  public CommandExecutor withLogger(Logger logger, LogLevel level) {
    return new CommandExecutor(spec -> {
      if (level.isEnabled(logger)) {
        level.log(logger, "Executing command: '%s'", spec.commandLine());
      }
      return spi.execute(spec);
    });
  }

}
