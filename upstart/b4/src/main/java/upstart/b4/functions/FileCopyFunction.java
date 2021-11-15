package upstart.b4.functions;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import upstart.b4.B4Function;
import upstart.b4.B4TargetContext;
import org.immutables.value.Value;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public class FileCopyFunction implements B4Function<FileCopyFunction.CopyConfig> {

  @Override
  public void run(CopyConfig config, B4TargetContext context) throws Exception {
    context.announce("Copying", config.to().toString()).run(() -> {
      Files.createDirectories(config.to().normalize().getParent());
      if (config.replaceExisting()) {
        Files.copy(config.from(), config.to(), StandardCopyOption.REPLACE_EXISTING);
      } else {
        Files.copy(config.from(), config.to());
      }
    });
  }

  @Override
  public void clean(CopyConfig config, B4TargetContext context) throws Exception {
    Files.deleteIfExists(config.to());
  }

  @Override
  public void cancel() {
  }

  @Value.Immutable
  @JsonDeserialize(as = ImmutableCopyConfig.class)
  public interface CopyConfig {
    Path from();
    Path to();
    boolean replaceExisting();
  }
}
