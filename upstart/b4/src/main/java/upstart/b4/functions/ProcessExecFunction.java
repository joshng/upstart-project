package upstart.b4.functions;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import upstart.b4.B4Function;
import upstart.b4.B4TaskContext;
import org.immutables.value.Value;
import upstart.commandExecutor.CommandResult;
import upstart.util.exceptions.UncheckedIO;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class ProcessExecFunction implements B4Function<ProcessExecFunction.ProcessExecConfig> {

  @Override
  public void clean(ProcessExecConfig config, B4TaskContext context) {
    config.cleanCommand().ifPresent(cleanCommand -> cleanCommand.run(context));
    config.outputFile().ifPresent(UncheckedIO.consumer(outputFile -> context.effect(
            "Deleting",
            outputFile.toString()
    ).run(() -> Files.deleteIfExists(outputFile))));
  }

  @Override
  public void run(ProcessExecConfig config, B4TaskContext context) {
    config.run(context);
  }

  @Override
  public void cancel() {
  }

  @Value.Immutable
  @JsonDeserialize(as = ImmutableProcessExecConfig.class)
  public interface ProcessExecConfig {
    Optional<Duration> timeout();
    String executable();
    List<String> args();
    Map<String, String> environment();
    boolean inheritParentEnvironment();
    Optional<Path> workDir();
    Optional<Path> outputFile();

    Optional<ProcessExecConfig> cleanCommand();

    default void run(B4TaskContext context) {
      Optional<CommandResult.ZeroExitCode> commandResult = context.effectCommand(executable(), b -> {
        if (!environment().isEmpty()) {
          if (inheritParentEnvironment()) b.inheritParentEnvironment();
          b.putAllEnvironment(environment());
        }
        timeout().ifPresent(b::timeout);
        b.workDir(workDir());
        if (outputFile().isPresent()) b.captureOutputString();
        return b.addAllArgs(args());
      });
      outputFile().ifPresent(UncheckedIO.consumer(outputFile -> context.effect(
              "Writing",
              outputFile.toString()
      ).run(() -> Files.write(outputFile, commandResult.orElseThrow().outputString().getBytes()))));
    }
  }
}
