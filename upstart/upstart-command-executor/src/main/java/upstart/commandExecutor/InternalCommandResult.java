package upstart.commandExecutor;

import org.immutables.value.Value;

@Value.Immutable
public interface InternalCommandResult {
  static ImmutableInternalCommandResult.Builder builder() {
    return ImmutableInternalCommandResult.builder();
  }

  static CommandResult.Completed toExternal(CommandSpec<?> commandSpec, InternalCommandResult result) {
    CommandResult.Completed externalResult;
    if (result.exitCode() == 0) {
      externalResult = CommandResult.ZeroExitCode.builder()
              .command(commandSpec)
              .outputString(result.outputString())
              .errorString(result.errorString())
              .build();
    } else {
      externalResult = CommandResult.NonZeroExitCode.builder()
              .command(commandSpec)
              .exitCode(result.exitCode())
              .outputString(result.outputString())
              .errorString(result.errorString())
              .build();
    }
    return externalResult;
  }

  int exitCode();
  String outputString();
  String errorString();

  default CommandResult.Completed toExternal(CommandSpec<?> commandSpec) {
    return toExternal(commandSpec, this);
  }
}
