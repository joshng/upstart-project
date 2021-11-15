package upstart.commandExecutor;

/**
 * Defines a policy for handling {@link CommandResult CommandResults} that are yielded by {@link CommandExecutor#run}.
 * Applied via {@link CommandSpecBuilder#policy}.
 * @see CommandSpecBuilder#policy
 * @see CommandPolicy#RequireZeroStatus
 * @see CommandPolicy#RequireCompleted
 * @see CommandPolicy#NoPolicy
 */
public interface CommandPolicy<R extends CommandResult> {
  /**
   * Yields an exception unless the invoked process completes with a zero exit-code
   * (see {@link CommandResult#requireZeroStatus} for details).
   * <p/>
   * Arranges for * {@link CommandExecutor#run} to return {@link CommandResult.ZeroExitCode}.
   * @see CommandSpecBuilder#policy
   * @see CommandResult#requireZeroStatus
   */
  CommandPolicy<CommandResult.ZeroExitCode> RequireZeroStatus = CommandResult::requireZeroStatus;

  /**
   * Yields an exception unless the invoked process runs to completion (ie, starts successfully and
   * does not time out; see {@link CommandResult#requireCompletion} for details).
   * <p/>
   * Arranges for {@link CommandExecutor#run} to return {@link CommandResult.Completed}
   * (a supertype which is either {@link CommandResult.ZeroExitCode} or {@link CommandResult.NonZeroExitCode}).
   * @see CommandSpecBuilder#policy
   * @see CommandResult#requireCompletion
   */
  CommandPolicy<CommandResult.Completed> RequireCompleted = CommandResult::requireCompletion;

  /**
   * Applies no policy, just arranges for {@link CommandExecutor#run} to return the {@link CommandResult}.
   */
  CommandPolicy<CommandResult> NoPolicy = result -> result;

  R apply(CommandResult result);
}
