package upstart.commandExecutor;

import upstart.config.UpstartModule;
import upstart.util.collect.Optionals;
import org.buildobjects.process.ProcBuilder;
import org.buildobjects.process.ProcResult;
import org.buildobjects.process.StartupException;
import org.buildobjects.process.TimeoutException;

import java.io.ByteArrayOutputStream;
import java.util.function.Supplier;

/**
 * A command executor implementation using the ProcBuilder package.
 * @see <a href = "https://github.com/fleipold/jproc"></a>
 *
 * Note that the timeout is here is a timeout for ProcBuilder and is not passed on to the
 * external process called by ProcBuilder.
 */
public class ProcBuilderCommandExecutor implements CommandExecutorSPI {

    @Override
    public InternalCommandResult execute(CommandSpec<?> commandSpec) throws CommandResult.CommandStartupException, CommandResult.CommandTimeoutException {

        ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        ProcBuilder procBuilder = new ProcBuilder(commandSpec.executable(), commandSpec.argArray())
                .withTimeoutMillis(commandSpec.timeout().toMillis())
                .ignoreExitStatus()
                .withOutputConsumer(commandSpec.stdoutConsumer())
                .withErrorStream(stderr);

        commandSpec.environment().forEach(procBuilder::withVar);

        commandSpec.workDir().ifPresent(path -> procBuilder.withWorkingDirectory(path.toFile()));

        Supplier<String> stdoutSupplier = () -> Optionals.asInstance(commandSpec.stdoutConsumer(), CapturingStreamConsumer.class)
                .map(CapturingStreamConsumer::getOutput)
                .orElse("[not captured]");
        try {
            ProcResult result = procBuilder.run();
            return InternalCommandResult.builder()
                    .exitCode(result.getExitValue())
                    .outputString(stdoutSupplier.get())
                    .errorString(stderr.toString())
                    .build();
        } catch (TimeoutException e) {
            String outputString = stdoutSupplier.get();
            String errorString = stderr.toString();
            throw new CommandResult.CommandTimeoutException(outputString, errorString, e);
        } catch (StartupException e) {
            throw new CommandResult.CommandStartupException(e);
        }
    }

    public static class CommandExecutorModule extends UpstartModule {
        @Override
        protected void configure() {
            bind(CommandExecutorSPI.class).to(ProcBuilderCommandExecutor.class);
        }
    }
}
