package upstart.commandExecutor;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;


class ProcBuilderCommandExecutorTest {
  private final CommandExecutor executor = new CommandExecutor(new ProcBuilderCommandExecutor());

  @Test
  void nonExistentBinary() {
    CommandResult result = executor.run(CommandSpec.of(Duration.ofSeconds(2), CommandPolicy.NoPolicy, "doesNotExist"));
    result.visit(new CommandResult.Visitor<Void>() {
      @Override
      public Void onStartupFailed(CommandResult.StartupFailed result) {
        assertThat(result.startupException()).hasCauseThat().hasCauseThat().isInstanceOf(IOException.class);
        return null;
      }

      @Override
      public Void onCompleted(CommandResult.Completed result) {
        throw new AssertionError("Expected command to not exist");
      }

      @Override
      public Void onTimedOut(CommandResult.TimedOut result) {
        throw new AssertionError("Expected command to not exist");
      }
    });
  }

  @Test
  void nonZeroExitCode() {
    String failingExecutable = "/usr/bin/false";
    CommandResult.Completed completed = executor.run(CommandSpec.of(Duration.ofSeconds(2), CommandPolicy.RequireCompleted, failingExecutable));
    completed.checkExitCode(1);
    CommandResult.UnexpectedExitCodeException exitCodeException = assertThrows(CommandResult.UnexpectedExitCodeException.class, completed::requireZeroStatus);
    assertThat(exitCodeException).hasMessageThat().contains("exit-code 1");
    assertThat(exitCodeException).hasMessageThat().contains(failingExecutable);
  }

  @Test
  void timeout() {
    CommandResult.CommandTimeoutException timeoutException = executor.run(CommandSpec.of(Duration.ofSeconds(1), CommandPolicy.NoPolicy, "/usr/bin/yes"))
            .visit(new CommandResult.Visitor<CommandResult.CommandTimeoutException>() {
              @Override
              public CommandResult.CommandTimeoutException onTimedOut(CommandResult.TimedOut result) {
                return result.timeoutException();
              }

              @Override
              public CommandResult.CommandTimeoutException onCompleted(CommandResult.Completed result) {
                throw new AssertionError("Expected process to time out");
              }

              @Override
              public CommandResult.CommandTimeoutException onStartupFailed(CommandResult.StartupFailed result) {
                throw new AssertionError("Expected process to time out");
              }
            });

    assertThat(timeoutException).hasMessageThat().contains("INCOMPLETE STDOUT:\ny\ny");
  }

  @Test
  void successfulCommand() {
    CommandResult result = executor.run(CommandSpec.of(Duration.ofSeconds(2), CommandPolicy.RequireZeroStatus, "/usr/bin/true"));
    assertThat(result).isInstanceOf(CommandResult.Completed.class);
    result.requireZeroStatus();
  }
}