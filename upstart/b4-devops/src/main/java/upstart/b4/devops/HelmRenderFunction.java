package upstart.b4.devops;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import upstart.b4.B4Function;
import upstart.b4.B4TargetContext;
import upstart.util.PairStream;
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
  public void clean(HelmConfig config, B4TargetContext context) throws Exception {
    Files.deleteIfExists(config.outputSpec());
  }

  @Override
  public void run(HelmConfig config, B4TargetContext context) throws Exception {
    Path parent = config.outputSpec().normalize().getParent();
    if (! parent.toFile().exists()) {
      Files.createDirectories(parent);
    }

    String spec = context.run(config.helmExecutable(),
            builder -> builder
                    .addArgs("template","--namespace", config.namespace())
                    .addArgs("--name-template", config.releaseName())
                    .addArg(config.chart())
                    .addArgs(PairStream.of(config.values()).flatMap(p -> setValue(config, p.getKey(), p.getValue())))
                    .captureOutputString()
    ) .outputString();

    try (Writer out = Files.newBufferedWriter(
            config.outputSpec(),
            StandardCharsets.UTF_8,
            StandardOpenOption.CREATE,
            StandardOpenOption.WRITE,
            StandardOpenOption.TRUNCATE_EXISTING
    )) {
      out.write(spec);
    }
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
