package upstart.b4;

import com.google.common.io.CharStreams;
import com.google.common.io.LineProcessor;
import upstart.commandExecutor.CommandExecutor;
import upstart.commandExecutor.CommandExecutorSPI;
import upstart.commandExecutor.CommandResult;
import upstart.commandExecutor.CommandSpec;
import upstart.commandExecutor.CommandSpecBuilder;
import upstart.commandExecutor.InternalCommandResult;
import upstart.util.exceptions.Fallible;
import upstart.util.exceptions.FallibleSupplier;
import upstart.util.strings.MoreStrings;
import upstart.util.collect.Optionals;
import upstart.util.exceptions.UncheckedInterruptedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

public class B4TaskContext {
  public static final String DRYRUN_LOG_MARKER = "[dryrun]";
  private static final AtomicInteger COMMAND_COUNTER = new AtomicInteger();
  private static final String MULTILINE_LOG_PREFIX = System.lineSeparator() + "| ";
  private static final String MULTILINE_LOG_BORDER = System.lineSeparator() + "-------------------------------------------------------------------------";
  private final Logger log;
  private final List<CommandSpec<?>> activeCommands = new ArrayList<>();
  private final TargetInvocation invocation;
  private final CommandExecutor commandExecutor;
  private final CountDownLatch cancelLatch = new CountDownLatch(1);
  private final boolean isDryRun;
  private volatile boolean canceled = false;
  private volatile B4Function.Verbosity currentVerbosity;

  @Inject
  public B4TaskContext(B4Application application, TargetInvocation invocation, CommandExecutorSPI realCommandExecutor) {
    this.invocation = invocation;
    this.commandExecutor = new CommandExecutor(new WrappedCommandExecutor(realCommandExecutor));
    this.log = LoggerFactory.getLogger(invocation.id().displayName());
    currentVerbosity = invocation.effectiveVerbosity();
    isDryRun = application.baseExecutionConfig().dryRun();
  }

  public String taskInstanceId() {
    return invocation.id().instanceId();
  }

  public boolean isDryRun() {
    return isDryRun;
  }

  public void yieldIfCanceled() {
    if (canceled) throw new B4CancellationException();
  }

  public void sleepOrCancel(Duration duration) {
    yieldIfCanceled();
    try {
      if (cancelLatch.await(duration.toNanos(), TimeUnit.NANOSECONDS)) {
        yieldIfCanceled();
      }
    } catch (InterruptedException e) {
      throw UncheckedInterruptedException.propagate(e);
    }
  }

  synchronized void cancel() {
    if (canceled) return;
    canceled = true;
    cancelLatch.countDown();
    activeCommands.forEach(CommandSpec::cancel);
  }

  public TargetInvocation getInvocation() {
    return invocation;
  }

  public TargetInvocation.Phases activePhases() {
    return invocation.effectivePhases();
  }

  public Optional<TargetInvocation.Phases> requestedPhases() {
    return invocation.phases();
  }

  public B4Function.Verbosity verbosity() {
    return invocation.effectiveVerbosity();
  }

  public CommandExecutor commandExecutor() {
    return commandExecutor;
  }

  public void effectCommand(String executable, String... args) {
    effectCommand(executable, builder -> builder.addArgs(args));
  }

  public void effectCommand(String executable, Function<CommandSpecBuilder<CommandResult.ZeroExitCode>, CommandSpecBuilder<CommandResult.ZeroExitCode>> builder) {
    if (!isDryRun) {
      alwaysRunCommand(executable, builder);
    } else {
      say(DRYRUN_LOG_MARKER, builder.apply(CommandSpec.builder(executable)).build().commandLine());
    }
  }


  public <R extends CommandResult> R alwaysRunCommand(String executable, Function<CommandSpecBuilder<CommandResult.ZeroExitCode>, CommandSpecBuilder<R>> builder) {
    return commandExecutor.run(executable, b -> {
      if (verbosity().logOutput) b.stdoutConsumer(new CommandOutputConsumer());
      return builder.apply(b);
    });
  }

  class WrappedCommandExecutor implements CommandExecutorSPI {
    private final CommandExecutorSPI wrapped;

    WrappedCommandExecutor(CommandExecutorSPI wrapped) {
      this.wrapped = wrapped;
    }

    @Override
    public InternalCommandResult execute(CommandSpec<?> commandSpec) throws CommandResult.CommandStartupException, CommandResult.CommandTimeoutException {
      Optional<String> cmdId = Optionals.onlyIfFrom(verbosity().logCommands, () -> String.format("CMD[%2d]", COMMAND_COUNTER.incrementAndGet()));
      cmdId.ifPresent(id -> say(id, commandSpec.commandLine()));
      synchronized (this) {
        yieldIfCanceled();
        activeCommands.add(commandSpec);
      }
      try {
        InternalCommandResult result = wrapped.execute(commandSpec);
        cmdId.ifPresent(id -> say(id, "DONE(" + result.exitCode() + "):", MoreStrings.truncateWithEllipsis(commandSpec.commandLine(), 40)));
        return result;
      } finally {
        synchronized (this) {
          activeCommands.remove(commandSpec);
        }
      }
    }
  }

  public void sayFormatted(String format, Object... args) {
    if (!isCommandLogEnabled()) return;
    say(String.format(format, args));
  }

  public void say(String... tokens) {
    say(B4Function.Verbosity.Info, tokens);
  }

  public void say(B4Function.Verbosity verbosity, String... tokens) {
    say(verbosity, String.join(" ", tokens));
  }

  public B4TaskContext say(String message) {
    return say(B4Function.Verbosity.Info, message);
  }

  public B4TaskContext say(B4Function.Verbosity verbosity, String message) {
    if (currentVerbosity.isEnabled(verbosity) && log.isInfoEnabled()) {
      if (message.contains(System.lineSeparator())) {
        message = MULTILINE_LOG_BORDER + MULTILINE_LOG_PREFIX + message.replaceAll(System.lineSeparator(), MULTILINE_LOG_PREFIX) + MULTILINE_LOG_BORDER;
      }
      log.info(message);
    }
    return this;
  }

  public <E extends Exception> void quietly(Fallible<E> block) throws E {
    getQuietly(() -> {
      block.runOrThrow();
      return null;
    });
  }

  public <T, E extends Exception> T getQuietly(FallibleSupplier<T, E> block) throws E {
    yieldIfCanceled();
    B4Function.Verbosity prev = currentVerbosity;
    setVerbosity(B4Function.Verbosity.Quiet);
    try {
      return block.getOrThrow();
    } finally {
      setVerbosity(prev);
    }
  }

  public void setVerbosity(B4Function.Verbosity verbosity) {
    currentVerbosity = verbosity;
  }

  public boolean isCommandLogEnabled() {
    return currentVerbosity.logCommands && log.isInfoEnabled();
  }

  public Effect effect(String... tokens) {
    return effect(B4Function.Verbosity.Info, tokens);
  }

  public Effect effect(B4Function.Verbosity verbosity, String... tokens) {
    return new RealEffect(verbosity, String.join(" ", tokens));
  }

  private class RealEffect implements Effect {
    private final B4Function.Verbosity verbosity;
    private final String message;

    public RealEffect(B4Function.Verbosity verbosity, String message) {
      this.verbosity = verbosity;
      this.message = message;
    }

    @Override
    public <T, E extends Exception> T get(FallibleSupplier<T, E> block, T dryRunResult) throws E {
      yieldIfCanceled();
      if (!isDryRun) {
        say(verbosity, message);
        T result = block.getOrThrow();
        return result;
      } else {
        say(DRYRUN_LOG_MARKER, message);
        return dryRunResult;
      }
    }
  }

  private class CommandOutputConsumer extends CommandSpec.StreamConsumer implements LineProcessor<Void> {

    @Override
    public void consumeOutput(InputStream stream) throws IOException {
      CharStreams.readLines(new InputStreamReader(stream, StandardCharsets.UTF_8), this);
    }

    @Override
    public boolean processLine(String line) throws IOException {
      say(line);
      return true;
    }

    @Override
    public Void getResult() {
      return null;
    }
  }

  public interface Effect {
    default <E extends Exception> void run(Fallible<E> block) throws E {
      get(() -> {
        block.runOrThrow();
        return null;
      }, null);
    }

    <T, E extends Exception> T get(FallibleSupplier<T, E> block, T dryRunResult) throws E;
  }
}
