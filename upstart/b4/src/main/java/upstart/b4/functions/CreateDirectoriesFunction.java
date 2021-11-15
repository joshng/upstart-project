package upstart.b4.functions;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import upstart.b4.B4Function;
import upstart.b4.B4TargetContext;
import org.immutables.value.Value;

import java.nio.file.Files;
import java.nio.file.Path;

public class CreateDirectoriesFunction implements B4Function<CreateDirectoriesFunction.CreateDirectoriesConfig> {

  @Override
  public void run(CreateDirectoriesConfig config, B4TargetContext context) throws Exception {
    context.announce("Creating directory", config.path().toString()).run(() -> {
      Files.createDirectories(config.path().normalize());
    });
  }

  @Override
  public void clean(CreateDirectoriesConfig config, B4TargetContext context) throws Exception {
  }

  @Override
  public void cancel() {
  }

  @Value.Immutable
  @JsonDeserialize(as = ImmutableCreateDirectoriesConfig.class)
  public interface CreateDirectoriesConfig {
    Path path();
  }
}
