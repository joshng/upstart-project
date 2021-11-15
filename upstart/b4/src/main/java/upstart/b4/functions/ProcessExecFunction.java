package upstart.b4.functions;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import upstart.b4.B4Function;
import upstart.b4.B4TargetContext;
import org.immutables.value.Value;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class ProcessExecFunction implements B4Function<ProcessExecFunction.ProcessExecConfig> {

  @Override
  public void clean(ProcessExecConfig config, B4TargetContext context) {
    config.cleanCommand().ifPresent(cleanCommand -> cleanCommand.run(context));
  }

  @Override
  public void run(ProcessExecConfig config, B4TargetContext context) {
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
    Optional<Path> workDir();
    boolean inheritParentEnvironment();

    Optional<ProcessExecConfig> cleanCommand();

    default void run(B4TargetContext context) {
      context.run(executable(), b -> {
        if (!environment().isEmpty()) {
          if (inheritParentEnvironment()) b.inheritParentEnvironment();
          b.putAllEnvironment(environment());
        }
        timeout().ifPresent(b::timeout);
        b.workDir(workDir());
        return b.addAllArgs(args());
      });
    }
  }
}
