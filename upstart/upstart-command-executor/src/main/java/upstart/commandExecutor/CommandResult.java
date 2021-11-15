package upstart.commandExecutor;

import org.immutables.value.Value;

/**
 * Conveys the result of executing an external process with a {@link CommandExecutor}. This is a "sum type"
 * (aka "disjoint union") with a supporting {@link #visit visitor-pattern API}
 * (<a href="https://www.google.com/search?q=visitor+pattern">google</a>). There are three variants:
 * <ul>
 *   <li>{@link Completed}</li>
 *   <li>{@link TimedOut}</li>
 *   <li>{@link StartupFailed}</li>
 * </ul>
 *
 * To perform specific behavior depending on a result's type, you may pass a {@link Visitor} to the {@link #visit} method.
 * <p/>
 * Some common behaviors are also supported by utility methods:
 * <ul>
 *   <li>{@link CommandResult#requireZeroStatus}: throws an exception if a result did not complete with a zero exit-code
 *       (which conventionally indicates failure)</li>
 *   <li>{@link CommandResult#requireCompletion}: throws an exception if the process {@link StartupFailed failed to start}
 *       or {@link TimedOut timed out}</li>
 * </ul>
 */
public interface CommandResult {

  CommandSpec<?> command();

  /**
   * Throws an exception if the command did not complete with a zero exit-code:
   * @returns this {@link Completed} if the process completed as expected, which provides access to the
   * {@link Completed#outputString outputString} and {@link Completed#errorString errorString} emitted by the
   * process stdout/stderr
   * @throws UnexpectedExitCodeException if the process completed with a non-zero exit-code
   * @throws CommandTimeoutException if the process failed to exit before the provided {@link CommandSpec#timeout}
   * @throws CommandStartupException if the process could not be started
   */
  default ZeroExitCode requireZeroStatus()
          throws UnexpectedExitCodeException, CommandTimeoutException, CommandStartupException
  {
    visit(CompletionVisitor.REQUIRE_ZERO_STATUS);
    return (ZeroExitCode) this;
  }

  /**
   * Throws an exception if the command failed to start, or timed out before completing. Does not check the
   * {@link Completed#exitCode exitCode} returned by the process.
   *
   * @return {@link CommandResult.Completed}, which is either {@link CommandResult.ZeroExitCode} or
   * {@link CommandResult.NonZeroExitCode}.
   *
   * @throws CommandStartupException if the process could not be started
   * @throws CommandTimeoutException if the process failed to exit before the provided {@link CommandSpec#timeout}
   *
   * @see Completed#checkExitCode
   */
  default Completed requireCompletion() throws CommandTimeoutException, CommandStartupException {
    return visit(CompletionVisitor.REQUIRE_COMPLETION);
  }

  /**
   * Invokes the appropriate callback on the given {@link Visitor}, depending on the specific type of this
   * {@link CommandResult}.
   * @returns the value returned by the visitor callback
   * @see CommandResult
   */
  <T> T visit(Visitor<T> visitor);

  interface Started extends CommandResult {
    /**
     * The string emitted by the process stdout (possibly incomplete, if this command {@link TimedOut} timed out)
     */
    String outputString();

    /**
     * The string emitted by the process stderr (possibly incomplete, if this command {@link TimedOut} timed out)
     */
    String errorString();
  }

  /**
   * Indicates that the process failed to exit before the expiration of the timeout specified via
   * {@link CommandSpec#timeout}, and was killed (probably with SIGTERM).
   * <p/>
   * Contains the (<strong>PARTIAL</strong>) {@link #outputString} and {@link #errorString} produced via the process
   * stdout/stderr before the timeout expired.
   *
   * @see #timeoutException
   */
  @Value.Immutable
  @Value.Style(visibility = Value.Style.ImplementationVisibility.PRIVATE)
  interface TimedOut extends Started {
    static TimedOutBuilder builder() {
      return new TimedOutBuilder();
    }

    /**
     * The <strong>PARTIAL</strong> string emitted by the process stdout, before the {@link CommandSpec#timeout} expired
     */
    @Override String outputString();

    /**
     * The <strong>PARTIAL</strong> string emitted by the process stderr, before the {@link CommandSpec#timeout} expired
     */
    @Override
    String errorString();

    /**
     * An exception which describes the timeout
     */
    CommandTimeoutException timeoutException();

    @Override
    default <T> T visit(Visitor<T> visitor) {
      return visitor.onTimedOut(this);
    }
  }

  /**
   * Indicates that the process was executed and exited on its own with the enclosed {@link #exitCode}.
   * Contains the complete {@link #outputString} and {@link #errorString} produced via the process stdout/stderr.
   *
   * @see #checkExitCode
   */
  interface Completed extends Started {
    /**
     * The exit-code returned by the completed process
     */
    int exitCode();

    /**
     * The string emitted by the process stdout
     */
    @Override
    String outputString();

    /**
     * The string emitted by the process stderr
     */
    @Override
    String errorString();

    @Override
    default <T> T visit(Visitor<T> visitor) {
      return visitor.onCompleted(this);
    }

    /**
     * Confirms that the process exited with the expected {@link #exitCode}.
     * @throws UnexpectedExitCodeException with a message containing the information from this {@link Completed result}
     * if the exitCode does not match the given expectedExitCode.
     */
    default Completed checkExitCode(int expectedExitCode) throws UnexpectedExitCodeException {
      if (exitCode() != expectedExitCode) {
        throw new UnexpectedExitCodeException(description());
      }
      return this;
    }

    default String description() {
      return String.format("Command returned exit-code %d: '%s'\nSTDOUT:\n%s\nSTDERR:\n%s",
              exitCode(), command().commandLine(), outputString(), errorString());
    }
  }

  @Value.Immutable
  @Value.Style(visibility = Value.Style.ImplementationVisibility.PRIVATE)
  interface ZeroExitCode extends Completed {
    static ZeroExitCodeBuilder builder() {
      return new ZeroExitCodeBuilder();
    }

    @Override
    default int exitCode() {
      return 0;
    }
  }

  @Value.Immutable
  @Value.Style(visibility = Value.Style.ImplementationVisibility.PRIVATE)
  interface NonZeroExitCode extends Completed {
    static NonZeroExitCodeBuilder builder() {
      return new NonZeroExitCodeBuilder();
    }
  }

  /**
   * Indicates that the requested process could not be started (eg, missing binary). Contains an exception which
   * may provide further information in {@link #startupException}.
   */
  @Value.Immutable
  @Value.Style(visibility = Value.Style.ImplementationVisibility.PRIVATE)
  interface StartupFailed extends CommandResult {
    static StartupFailedBuilder builder() {
      return new StartupFailedBuilder();
    }

    CommandStartupException startupException();

    @Override
    default <T> T visit(Visitor<T> visitor) {
      return visitor.onStartupFailed(this);
    }
  }

  /**
   * Visitor-pattern callbacks for handling the various types of {@link CommandResult}s.
   * @see CommandResult
   * @see CompletionVisitor
   */
  interface Visitor<T> {

    T onCompleted(Completed result);
    T onTimedOut(TimedOut result);
    T onStartupFailed(StartupFailed result);
  }

  /**
   * A utility base-class that throws exceptions for any incomplete command executions ({@link StartupFailed} or {@link TimedOut}).
   * @see #requireCompletion
   * @see #requireZeroStatus
   */
  abstract class CompletionVisitor<T> implements Visitor<T> {
    public static final Visitor<Completed> REQUIRE_ZERO_STATUS = new CompletionVisitor<Completed>() {
      @Override
      public Completed onCompleted(Completed result) {
        return result.checkExitCode(0);
      }
    };
    public static final Visitor<Completed> REQUIRE_COMPLETION = new CompletionVisitor<Completed>() {
      @Override
      public Completed onCompleted(Completed result) {
        return result;
      }
    };

    @Override
    public T onStartupFailed(StartupFailed result) {
      throw result.startupException();
    }

    @Override
    public T onTimedOut(TimedOut result) {
      throw result.timeoutException();
    }
  }

  /**
   * @see StartupFailed
   */
  class CommandStartupException extends RuntimeException {
    public CommandStartupException(Throwable cause) {
      super(cause);
    }
  }


  /**
   * @see TimedOut
   */
  class CommandTimeoutException extends RuntimeException {
    private String outputString;
    private final String errorString;

    public CommandTimeoutException(String outputString, String errorString, Throwable cause) {
      super("Process timed out.\nINCOMPLETE STDOUT:\n" + outputString + "\nINCOMPLETE STDERR:\n" + errorString, cause);
      this.outputString = outputString;
      this.errorString = errorString;
    }

    public String getOutputString() {
      return outputString;
    }

    public String getErrorString() {
      return errorString;
    }
  }

  /**
   * @see Completed#checkExitCode
   */
  class UnexpectedExitCodeException extends IllegalStateException {
    public UnexpectedExitCodeException(String message) {
      super(message);
    }
  }
}
