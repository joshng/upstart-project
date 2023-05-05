package upstart.commandExecutor;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import upstart.util.strings.MoreStrings;
import org.immutables.value.Value;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.concurrent.CancellationException;
import java.util.stream.Stream;

/**
 * A parameter-object for invoking external processes with a {@link CommandExecutor}.
 * @see CommandSpec#of
 * @see CommandSpec#builder
 * @see CommandExecutor
 */
@Value.Immutable
@Value.Style(visibility = Value.Style.ImplementationVisibility.PRIVATE)
public abstract class CommandSpec<ResultType extends CommandResult> {
  public static final Duration NO_TIMEOUT = Duration.ofMillis(Long.MAX_VALUE);
  private static final String[] NO_ARGS = new String[0];

  public static <R extends CommandResult> CommandSpec<R> of(Duration timeout, CommandPolicy<R> completionPolicy, String executable, String... args) {
    return builder(timeout, executable)
            .addArgs(args)
            .policy(completionPolicy)
            .build();
  }

  /**
   * Prepares a {@link CommandSpecBuilder} for the given executable with no timeout, which will throw an exception unless
   * the process exits normally (see {@link CommandPolicy#RequireZeroStatus})
   */
  public static Builder<CommandResult.ZeroExitCode> builder(String executable) {
    return builder(NO_TIMEOUT, executable);
  }

  /**
   * Prepares a {@link CommandSpecBuilder} for the given executable with the given timeout, which will throw an exception unless
   * the process exits normally (see {@link CommandPolicy#RequireZeroStatus})
   */
  public static Builder<CommandResult.ZeroExitCode> builder(Duration timeout, String executable) {
    return builder(timeout, CommandPolicy.RequireZeroStatus, executable);
  }

  public static <R extends CommandResult> Builder<R> builder(Duration timeout, CommandPolicy<R> completionPolicy, String executable) {
    return new Builder<>()
            .timeout(timeout)
            .executable(executable)
            .policy(completionPolicy);
  }

  public static String commandLine(String executable, List<String> args) {
    if (args.isEmpty()) return executable;
    StringJoiner joiner = new StringJoiner(" ");
    joiner.add(executable);
    args.forEach(joiner::add);
    return joiner.toString();
  }

  /**
   * The time to allow for the command to complete, after which it will be killed.
   * @see CommandResult.TimedOut
   */
  @Value.Default
  public Duration timeout() {
    return NO_TIMEOUT;
  }

  /**
   * The path to the executable to be invoked
   */
  public abstract String executable();

  /**
   * The command-line parameters to pass to the executable
   */
  public abstract List<String> args();

  /**
   * Additional environment variables for the executable
   */
  public abstract Map<String, String> environment();

  /**
   * Working directory for the executable (defaults to user.dir, aka CWD, if empty)
   */
  public abstract Optional<Path> workDir();

  /**
   * Handler for stdout from the process. Defaults to a buffer which can be obtained from {@link CommandResult.Started#outputString()}
   * @return
   */
  @Value.Auxiliary
  @Value.Default
  public StreamConsumer stdoutConsumer() {
    return new CapturingStreamConsumer();
  }

  /**
   * Determines the handling of command completion.
   * @return
   */
  public abstract CommandPolicy<ResultType> completionPolicy();

  public boolean isOutputCaptured() {
    return stdoutConsumer() instanceof CapturingStreamConsumer;
  }

  public void cancel() {
    stdoutConsumer().cancel();
  }

  /**
   * A space-separated rendering of the command-line ({@link #executable} + {@link #args})
   */
  @Value.Lazy
  public String commandLine() {
    return commandLine(executable(), args());
  }

  @Value.Lazy
  String[] argArray() {
    return args().toArray(NO_ARGS);
  }

  public abstract static class StreamConsumer implements org.buildobjects.process.StreamConsumer {
    private Thread consumingThread = null;
    private boolean canceled = false;
    private InputStream inputStream = null;

    protected abstract void consumeOutput(InputStream stream) throws IOException;

    protected boolean isCanceled() {
      return canceled;
    }

    @Override
    public final void consume(InputStream stream) throws IOException {
      Thread currentThread = Thread.currentThread();
      synchronized (this) {
        if (canceled) throw new CancellationException("Process was canceled");
        consumingThread = currentThread;
        inputStream = stream;
      }
      try {
        try {
          consumeOutput(stream);
        } finally {
          synchronized (this) {
            consumingThread = null;
            inputStream = null;
            if (canceled) Thread.interrupted();
          }
        }
      } catch (Throwable e) {
        if (canceled && !(e instanceof CancellationException)) {
          CancellationException softenedException = new CancellationException("Process was canceled");
          softenedException.addSuppressed(e);
          throw softenedException;
        } else {
          throw e;
        }
      }
    }

    public synchronized final void cancel() {
      if (canceled) return;
      canceled = true;
      Thread thread = consumingThread;
      if (thread != null) {
        try {
          inputStream.close();
        } catch (IOException e) {
          // ignore
        }
        thread.interrupt();
      }
    }
  }

  public static class Builder<ResultType extends CommandResult> extends CommandSpecBuilder<ResultType> {
    private boolean stdoutIsCaptured = false;

    public Builder<ResultType> inheritParentEnvironment() {
      return putAllEnvironment(System.getenv());
    }

    public Builder<ResultType> captureOutputString() {
      stdoutIsCaptured = true;
      return stdoutConsumer(new CapturingStreamConsumer());
    }

    public boolean stdoutIsCaptured() {
      return stdoutIsCaptured;
    }

    /**
     * Applies the given {@link CommandPolicy} to the {@link CommandResult} yielded by {@link CommandExecutor#run running} this command.
     * @return This {@link CommandSpecBuilder}, with a modified result-type ({@link R}) to match the new policy's behavior
     */
    @SuppressWarnings("unchecked")
    public <R extends CommandResult> Builder<R> policy(CommandPolicy<R> newPolicy) {
      return ((Builder<R>) this).completionPolicy(newPolicy);
    }

    @CanIgnoreReturnValue
    public Builder<ResultType> noTimeout() {
      return timeout(NO_TIMEOUT);
    };

    @CanIgnoreReturnValue
    public Builder<ResultType> addSpaceSeparatedArgs(String args) {
      return addArgs(MoreStrings.splitOnSpaces(args));
    }

    @CanIgnoreReturnValue
    public Builder<ResultType> addArgs(Stream<String> argStream) {
      return addAllArgs(argStream::iterator);
    }

    @CanIgnoreReturnValue
    public Builder<ResultType> addArg(String element) {
      return addArgs(element);
    }
  }
}
