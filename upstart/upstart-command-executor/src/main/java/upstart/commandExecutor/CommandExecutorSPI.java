package upstart.commandExecutor;

/**
 * A mechanism for executing external processes, to support mocking these commands in tests.
 * @see MockCommandExtension
 */
public interface CommandExecutorSPI {
  InternalCommandResult execute(CommandSpec<?> commandSpec) throws CommandResult.CommandStartupException, CommandResult.CommandTimeoutException;
}
