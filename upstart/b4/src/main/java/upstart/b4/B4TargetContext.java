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
import upstart.util.MoreStrings;
import upstart.util.Optionals;
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

public class B4TargetContext {
  private static final AtomicInteger COMMAND_COUNTER = new AtomicInteger();
  private static final String MULTILINE_LOG_PREFIX = "\n| ";
  private static final String MULTILINE_LOG_BORDER = "\n-------------------------------------------------------------------------";
  private final B4StateStore stateStore;
  private final Logger log;
  private final List<CommandSpec<?>> activeCommands = new ArrayList<>();
  private final TargetInvocation invocation;
  private final CommandExecutor commandExecutor;
  private final CountDownLatch cancelLatch = new CountDownLatch(1);
  private volatile boolean canceled = false;
  private volatile boolean logEnabled;

  @Inject
  public B4TargetContext(TargetInvocation invocation, CommandExecutorSPI realCommandExecutor, B4StateStore stateStore) {
    this.invocation = invocation;
    this.commandExecutor = new CommandExecutor(new WrappedCommandExecutor(realCommandExecutor));
    this.stateStore = stateStore;
    this.log = LoggerFactory.getLogger(invocation.id().displayName());
    logEnabled = invocation.effectiveVerbosity().logCommands;
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

  public CommandResult.ZeroExitCode run(String executable, String... args) {
    return run(executable, builder -> builder.addArgs(args));
  }

  public <R extends CommandResult> R run(String executable, Function<CommandSpecBuilder<CommandResult.ZeroExitCode>, CommandSpecBuilder<R>> builder) {
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
    if (!isLogEnabled()) return;
    say(String.format(format, args));
  }

  public void say(String... tokens) {
    say(String.join(" ", tokens));
  }

  public B4TargetContext say(String message) {
    if (isLogEnabled()) {
      if (message.contains("\n")) {
        message = MULTILINE_LOG_BORDER + MULTILINE_LOG_PREFIX +  message.replaceAll("\n", MULTILINE_LOG_PREFIX) +  MULTILINE_LOG_BORDER;
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
    boolean wasEnabled = logEnabled;
    setLogEnabled(false);
    try {
      return block.getOrThrow();
    } finally {
      setLogEnabled(wasEnabled);
    }
  }

  public void setLogEnabled(boolean enabled) {
    logEnabled = enabled;
  }

  public boolean isLogEnabled() {
    return logEnabled && log.isInfoEnabled();
  }

  public Announcer announce(String... tokens) {
    return new Announcer(String.join(" ", tokens));
  }

  public class Announcer {
    private final String message;

    public Announcer(String message) {
      this.message = message;
    }

    public <E extends Exception> void run(Fallible<E> block) throws E {
      get(() -> {
        block.runOrThrow();
        return null;
      });
    }

    public <T, E extends Exception> T get(FallibleSupplier<T, E> block) throws E {
      yieldIfCanceled();
      say(message);
      T result = block.getOrThrow();
      say("DONE:", message);
      return result;
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
}
