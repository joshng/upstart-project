package upstart.commandExecutor;

import org.mockito.Mockito;
import org.mockito.verification.VerificationMode;

import java.time.Duration;
import java.util.concurrent.TimeoutException;

import static org.mockito.ArgumentMatchers.any;

/**
 * @see MockCommandExtension
 */
public class CommandFixture {
  final CommandExecutorSPI mock = Mockito.mock(CommandExecutorSPI.class);

  /**
   * arrange for all commands to succeed with exitCode=0 (unless otherwise mocked directly)
   */
  public void succeedByDefault() {
    Mockito.when(mock.execute(any(CommandSpec.class)))
            .thenReturn(CommandFixture.successResult());
  }

  /**
   * Arrange for a command matching the given spec to succeed with exitCode=0
   */
  public void mockSuccess(CommandSpec commandSpec) {
    mockExitCode(commandSpec, 0);
  }

  /**
   * Arrange for a command matching the given spec to complete with the given exitCode
   */
  public void mockExitCode(CommandSpec commandSpec, int exitCode) {
    Mockito.when(mock.execute(commandSpec))
            .thenReturn(completedResult(exitCode));
  }

  /**
   * Arrange for a command matching the given spec to time out
   */
  public void mockTimeout(CommandSpec<?> commandSpec) {
    Mockito.when(mock.execute(commandSpec)).thenThrow(new CommandResult.CommandTimeoutException("<fake>", "", null));
  }

  /**
   * Arrange for a command matching the given spec to yield the given result
   */
  public void mockCommand(CommandSpec<?> commandSpec, InternalCommandResult result) {
    Mockito.when(mock.execute(commandSpec)).thenReturn(result);
  }

  /**
   * Verify that a command matching the given spec parameters was invoked
   */
  public void verifyCommand(Duration timeout, String executable, CommandPolicy<?> completionPolicy, String... args) {
    verifyCommand(CommandSpec.of(timeout, completionPolicy, executable, args));
  }

  /**
   * Verify that a command matching the given spec was invoked
   */
  public void verifyCommand(CommandSpec<?> commandSpec) {
    verify().execute(commandSpec);
  }

  /**
   * Verify that a command matching the given spec was invoked with the given {@link VerificationMode}
   * @see VerificationMode
   */
  public void verifyCommand(VerificationMode verificationMode, CommandSpec<?> commandSpec) {
    verify(verificationMode).execute(commandSpec);
  }

  private CommandExecutorSPI verify(VerificationMode verificationMode) {
    return Mockito.verify(mock, verificationMode);
  }

  /**
   * Exposes the underlying {@link Mockito}-provided {@link CommandExecutor}, to enable arbitrary mockery behavior
   * @see #verifyCommand
   */
  public CommandExecutorSPI mockitoCommandExecutor() {
    return mock;
  }

  private CommandExecutorSPI verify() {
    return Mockito.verify(mock);
  }

  public static CommandResult.CommandTimeoutException timedOutException() {
    return new CommandResult.CommandTimeoutException("", "", new TimeoutException("mock timeout"));
  }

  public static InternalCommandResult successResult() {
    return completedResult(0);
  }

  public static InternalCommandResult completedResult(int exitCode) {
    return completedResult(exitCode, "<mock output>");
  }

  public static InternalCommandResult completedResult(int exitCode, String outputString) {
    return InternalCommandResult.builder()
            .exitCode(exitCode)
            .outputString(outputString)
            .errorString("")
            .build();
  }
}
