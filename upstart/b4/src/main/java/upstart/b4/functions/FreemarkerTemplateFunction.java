package upstart.b4.functions;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import freemarker.template.Configuration;
import freemarker.template.Template;
import upstart.b4.B4Function;
import upstart.b4.B4TaskContext;
import upstart.config.annotations.DeserializedImmutable;

import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;

public class FreemarkerTemplateFunction implements B4Function<FreemarkerTemplateFunction.FtlTemplateConfig> {
  private static final LoadingCache<Path, Configuration> CONFIGS = CacheBuilder.newBuilder()
          .build(new CacheLoader<>() {
            @Override
            public Configuration load(Path templateRoot) throws Exception {
              Configuration conf = new Configuration(Configuration.VERSION_2_3_31);
              conf.setInterpolationSyntax(Configuration.SQUARE_BRACKET_INTERPOLATION_SYNTAX);
              conf.setTagSyntax(Configuration.SQUARE_BRACKET_TAG_SYNTAX);
              conf.setDirectoryForTemplateLoading(templateRoot.toFile());
              conf.setDefaultEncoding("UTF-8");
              conf.setLogTemplateExceptions(false);
              return conf;
            }
          });

  @Override
  public void run(FtlTemplateConfig config, B4TaskContext context) throws Exception {
    Template template = CONFIGS.getUnchecked(config.templateRoot()).getTemplate(config.template().toString());
//    String result = MoreStrings.interpolateTokens(template, config.placeholderPattern(), config.args());
    context.effect("Rendering", config.to().toString()).run(() -> {
      Files.createDirectories(config.to().normalize().getParent());
      OpenOption[] openOption = config.replaceExisting()
              ? new OpenOption[]{StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE}
              : new OpenOption[]{StandardOpenOption.CREATE_NEW};
      try (var out = Files.newBufferedWriter(config.to(), openOption)) {
        template.process(config.args(), out);
      }
    });
  }

  @Override
  public void clean(FtlTemplateConfig config, B4TaskContext context) throws Exception {
    context.effect("Deleting rendered template file", config.to().toString())
            .run(() -> Files.deleteIfExists(config.to()));
  }

  @Override
  public void cancel() {
  }

  @DeserializedImmutable
  public interface FtlTemplateConfig {
    Path templateRoot();
    Path template();
    Path to();
    Map<String, Object> args();
    boolean replaceExisting();
  }
}
