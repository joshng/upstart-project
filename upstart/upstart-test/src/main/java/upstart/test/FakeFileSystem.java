package upstart.test;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdScalarDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import com.google.common.jimfs.WatchServiceConfiguration;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

public class FakeFileSystem extends SingletonParameterResolver<FileSystem> implements BeforeEachCallback {
  static final Configuration POSIX_ATTRVIEW_UNIX_CONFIG = Configuration.unix().toBuilder().setAttributeViews("basic", "posix").build();
  protected FakeFileSystem() {
    super(FileSystem.class);
  }

  public static FileSystem buildFileSystem(Configuration config) {
    return Jimfs.newFileSystem(POSIX_ATTRVIEW_UNIX_CONFIG);
  }

  protected FileSystem createContext(ExtensionContext extensionContext) {

    Optional<FakeFileSystemTest> annotation = ExtensionContexts.findNearestAnnotation(FakeFileSystemTest.class, extensionContext);
    FakeFilesystemConfig config = annotation.<FakeFilesystemConfig>map(anno -> FakeFilesystemConfig.builder()
            .workingDirectory(anno.workingDirectory())
            .watchServicePollInterval(Duration.ofMillis(anno.watchServicePollIntervalMs()))
            .build()
    ).orElse(FakeFilesystemConfig.Default);

    Optional<? extends UpstartTestBuilder> optionalTestBuilder = UpstartExtension.getOptionalTestBuilder(extensionContext);

    return optionalTestBuilder.map(testBuilder -> setupTest(testBuilder, config))
            .orElseGet(() -> buildFileSystem(config));

  }

  public static FileSystem setupTest(UpstartTestBuilder testBuilder, FakeFilesystemConfig config) {
    FileSystem fs = buildFileSystem(config);
    testBuilder.overrideInstance(FileSystem.class, fs);
    testBuilder.registerConfigModule(new FakeFileSystemJacksonModule(fs));
    return fs;
  }

  public static FileSystem buildFileSystem(FakeFilesystemConfig config) {
    Configuration.Builder builder = Configuration.unix().toBuilder();
    config.watchServicePollInterval().ifPresent(interval -> builder.setWatchServiceConfiguration(WatchServiceConfiguration.polling(interval.toNanos(), TimeUnit.NANOSECONDS)));
    config.workingDirectory()
            .ifPresent(dir -> {
              if (dir.equals("$PWD")) {
                dir = System.getProperty("user.dir");
              }
              builder.setWorkingDirectory(dir);
            });

    return buildFileSystem(builder.build());
  }

  @Override
  public void beforeEach(ExtensionContext context) throws Exception {
    // ensure that the FakeFileSystem is installed BeforeEach, even if the test-code doesn't use it until later
    getOrCreateContext(context);
  }

  public static class FakeFileSystemJacksonModule extends SimpleModule {
    public FakeFileSystemJacksonModule(FileSystem fs) {
      addDeserializer(Path.class, new FileSystemAwarePathDeserializer(fs));
    }

    public static class FileSystemAwarePathDeserializer extends StdScalarDeserializer<Path> {
      private final FileSystem fs;

      public FileSystemAwarePathDeserializer(FileSystem fs) {
        super(Path.class);
        this.fs = fs;
      }

      @Override
      public Path deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        if (!p.hasToken(JsonToken.VALUE_STRING)) {
          return (Path) ctxt.handleUnexpectedToken(Path.class, p);
        }

        String value = p.getText();

        return fs.getPath(value);
      }
    }
  }
}
