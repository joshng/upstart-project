package upstart.test;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdScalarDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.google.common.io.Resources;
import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import com.google.common.jimfs.WatchServiceConfiguration;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import upstart.util.reflect.Reflect;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static com.google.common.base.Preconditions.checkArgument;

public class FakeFileSystemExtension extends BaseSingletonParameterResolver<FileSystem> implements BeforeEachCallback {
  protected FakeFileSystemExtension() {
    super(FileSystem.class);
  }

  public static FileSystem buildFileSystem(Configuration config) {
    return Jimfs.newFileSystem(config);
  }

  public static void writeFileFixture(String resourceName, FileSystem fs, String path) {
    writeFileFixture(resourceName, fs.getPath(path));
  }

  public static void writeFileFixture(String resourceName, Path dest) {
    checkArgument(
            dest.getFileSystem().getClass().getSimpleName().equals("JimfsFileSystem"),
            "Path expected to be in a JimfsFileSystem",
            dest
    );
    try {
      Path parent = dest.getParent();
      if (parent != null) Files.createDirectories(parent);
      try (
              var in = Resources.getResource(resourceName).openStream();
              var out = Files.newOutputStream(dest)
      ) {
        in.transferTo(out);
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public FileSystem createContext(ExtensionContext extensionContext) {

    List<FakeFileSystemTest> annotations = ExtensionContexts.findTestAnnotations(
            FakeFileSystemTest.class,
            Reflect.LineageOrder.SubclassBeforeSuperclass,
            extensionContext
    ).toList();

    String workdir = annotations.stream()
            .filter(a -> !a.workingDirectory().isEmpty())
            .findFirst()
            .map(FakeFileSystemTest::workingDirectory)
            .orElse(FakeFileSystemTest.DEFAULT_WORKING_DIRECTORY);
    long pollInterval = annotations.stream()
            .filter(a -> a.watchServicePollIntervalMs() > 0)
            .findFirst()
            .map(FakeFileSystemTest::watchServicePollIntervalMs)
            .orElse(FakeFileSystemTest.DEFAULT_WATCH_SERVICE_POLL_INTERVAL_MS);

    FakeFilesystemConfig config = FakeFilesystemConfig.builder()
            .workingDirectory(workdir)
            .watchServicePollInterval(Duration.ofMillis(pollInterval))
            .build();

    Optional<? extends UpstartTestBuilder> optionalTestBuilder = UpstartExtension.getOptionalTestBuilder(
            extensionContext);

    FileSystem fileSystem = optionalTestBuilder.map(testBuilder -> setupTest(testBuilder, config))
            .orElseGet(() -> buildFileSystem(config));

    Set<String> uniquePaths = new HashSet<>();
    annotations.stream()
            .map(FakeFileSystemTest::value)
            .flatMap(Arrays::stream)
            .filter(file -> uniquePaths.add(file.path())) // skip paths on superclasses that have been overwritten
            .forEach(fakeFile -> writeFileFixture(fakeFile.fromResource(), fileSystem, fakeFile.path()));

    return fileSystem;
  }

  public static FileSystem setupTest(UpstartTestBuilder testBuilder, FakeFilesystemConfig config) {
    FileSystem fs = buildFileSystem(config);
    testBuilder.overrideInstance(FileSystem.class, fs);
    testBuilder.registerConfigModule(new FakeFileSystemJacksonModule(fs));
    return fs;
  }

  public static FileSystem buildFileSystem(FakeFilesystemConfig config) {
    Configuration.Builder builder = Configuration.unix().toBuilder().setAttributeViews("basic", "posix");
    config.watchServicePollInterval().ifPresent(interval -> builder.setWatchServiceConfiguration(
            WatchServiceConfiguration.polling(interval.toNanos(), TimeUnit.NANOSECONDS)
    ));
    config.workingDirectory().ifPresent(dir -> {
      if (dir.equals("$PWD")) dir = System.getProperty("user.dir");
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
