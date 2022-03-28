package upstart.b4.devops;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import upstart.b4.B4Function;
import upstart.b4.B4TaskContext;
import upstart.util.collect.PairStream;
import org.immutables.value.Value;

import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.stream.Stream;

public class HelmRenderFunction implements B4Function<HelmRenderFunction.HelmConfig> {

  private static Stream<String> setValue(HelmConfig config, String k, String v) {
    if (config.setStrings()) {
      return Stream.of("--set-string", String.format("%s=%s", k, v));
    } else {
      return Stream.of("--set", String.format("%s=%s", k, v));
    }
  }

  @Override
  public void clean(HelmConfig config, B4TaskContext context) throws Exception {
    context.effect("Deleting existing files")
            .run(() -> Files.deleteIfExists(config.outputSpec()));
  }

  @Override
  public void run(HelmConfig config, B4TaskContext context) throws Exception {
    String spec = context.alwaysRunCommand(config.helmExecutable(),
            builder -> builder
                    .addArgs("template","--namespace", config.namespace())
                    .addArgs("--name-template", config.releaseName())
                    .addArg(config.chart())
                    .addArgs(PairStream.of(config.values()).flatMap(p -> setValue(config, p.getKey(), p.getValue())))
                    .captureOutputString()
    ).outputString();

    context.effect("Writing rendered helm-chart to", config.outputSpec().toString()).run(() -> {
      Files.createDirectories(config.outputSpec().normalize().getParent());
      try (Writer out = Files.newBufferedWriter(
              config.outputSpec(),
              StandardCharsets.UTF_8,
              StandardOpenOption.CREATE,
              StandardOpenOption.WRITE,
              StandardOpenOption.TRUNCATE_EXISTING
      )) {
        out.write(spec);
      }
    });
  }

  @Override
  public void cancel() {
  }

  @Value.Immutable
  @JsonDeserialize(as = ImmutableHelmConfig.class)
  public interface HelmConfig {
    String helmExecutable();
    String releaseName();
    String namespace();
    String chart();
    Map<String,String> values();
    Path outputSpec();
    boolean setStrings();
  }
}
