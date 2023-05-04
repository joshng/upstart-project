package upstart.util.concurrent;

import upstart.util.MorePaths;
import upstart.util.exceptions.Fallible;
import upstart.util.exceptions.FallibleConsumer;
import upstart.util.exceptions.FallibleFunction;
import org.immutables.value.Value;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileAttribute;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

@Value.Immutable
@Value.Style(visibility = Value.Style.ImplementationVisibility.PRIVATE, builderVisibility = Value.Style.BuilderVisibility.PACKAGE)
public interface TempFileFactory {

  FileAttribute<?>[] DEFAULT_FILE_ATTRIBUTES = {};

  static Builder builder(FileSystem fileSystem) {
    return new TempFileFactoryBuilder().fileSystem(fileSystem);
  }

  static Builder builder(Path path) {
    return new TempFileFactoryBuilder().tempFileDir(path);
  }

  @Value.Default
  default FileSystem fileSystem() {
    return tempFileDir().getFileSystem();
  }

  @Value.Default
  default Path tempFileDir() {
    return fileSystem().getPath(System.getProperty("java.io.tmpdir", "/tmp"));
  }

  Optional<String> tempFilePrefix();

  Optional<String> tempFileSuffix();

  @Value.Default
  default FileAttribute<?>[] fileAttributes() {
    return DEFAULT_FILE_ATTRIBUTES;
  }

  default TempFileFactory ensureWriteable() throws IOException {
    MorePaths.ensureWritableDir(tempFileDir());
    return this;
  }

  @Value.Check
  default void checkFileSystem() {
    checkArgument(fileSystem() == tempFileDir().getFileSystem(), "FileSystem must match tempFileDir");
  }

  default Path createTempFile() throws IOException {
    return Files.createTempFile(tempFileDir(), tempFilePrefix().orElse(null), tempFileSuffix().orElse(null), fileAttributes());
  }

  /**
   * Provides a mechanism for "atomically" writing a file in a given location without exposing it there before its
   * contents have been fully written. <p/>
   *
   * Achieves this by first providing a temp-file to accept the intended content, and then atomically moving the
   * temp-file to its final location after it has been written and closed.<p/>
   *
   * Whether or not the process succeeds, this method tries to ensure that the temp-file is removed before returning,
   * either by moving it to its final location, or by deleting it if something goes wrong.
   *
   * @param finalFileLocation the {@link Path} for the file after it has been written
   * @param writer a function that must write the desired content into the provided {@link Path}
   * @return the value returned by the <code>writer</code> function
   */
  default <T, E extends Exception> T writeFileAtomically(Path finalFileLocation, FallibleFunction<Path, T, E> writer) throws E, IOException {
    return atomicWriter(finalFileLocation).commitWith(writer);
  }

  default AtomicFileWriter atomicWriter(Path finalFileLocation) throws IOException {
    return AtomicFileWriter.builder()
            .tempFile(createTempFile())
            .finalFileLocation(finalFileLocation)
            .build();
  }


  /**
   * Provides a mechanism for "atomically" writing a file in a given location without exposing it there before its
   * contents have been fully written. <p/>
   *
   * Achieves this by first providing a temp-file to accept the intended content, and then atomically moving the
   * temp-file to its final location after it has been written and closed.<p/>
   *
   * Whether or not the process succeeds, this method tries to ensure that the temp-file is removed before returning,
   * either by moving it to its final location, or by deleting it if something goes wrong.
   *
   * @param finalFileLocation the {@link Path} for the file after it has been written
   * @param writer a consumer that must write the desired content into the provided {@link Path}
   */
  default <E extends Exception> void writeFileAtomically(Path finalFileLocation, FallibleConsumer<Path, E> writer) throws E, IOException {
    writeFileAtomically(finalFileLocation, tmpFile -> {
      writer.acceptOrThrow(tmpFile);
      return null;
    });
  }

  interface Builder {
    Builder fileSystem(FileSystem fileSystem);
    Builder tempFileDir(Path tempFileDir);
    Builder tempFilePrefix(String tempFilePrefix);
    Builder tempFileSuffix(String tempFileSuffix);
    Builder tempFilePrefix(Optional<String> tempFilePrefix);
    Builder tempFileSuffix(Optional<String> tempFileSuffix);

    TempFileFactory build();
  }

  @Value.Immutable
  abstract class AtomicFileWriter implements Closeable {
    static AtomicFileWriterBuilder builder() {
      return new AtomicFileWriterBuilder();
    }
    private final AtomicBoolean closed = new AtomicBoolean(false);

    public abstract Path tempFile();
    public abstract Path finalFileLocation();

    public TransactionalFile transactionalFile(Closeable writer) {
      return new TransactionalFile(writer, this);
    }

    public <T, E extends Exception> T commitWith(FallibleFunction<Path, T, E> writer) throws E, IOException {
      try (var ignored = this) {
        T result = writer.applyOrThrow(tempFile());
        commit();
        return result;
      }
    }

    public <E extends Exception> Path commitWith(FallibleConsumer<Path, E> writer) throws E, IOException {
      try (var ignored = this) {
        writer.acceptOrThrow(tempFile());
        commit();
        return finalFileLocation();
      }
    }

    public Path commitWith(Closeable writer) throws IOException {
      return commitAfter(writer::close);
    }

    public <E extends Exception> Path commitAfter(Fallible<E> writer) throws E, IOException {
      try (var ignored = this) {
        writer.runOrThrow();
        commit();
        return finalFileLocation();
      }
    }

    public void commit() throws IOException {
      checkState(closed.compareAndSet(false, true), "Already closed");
      try {
        Files.move(tempFile(), finalFileLocation(), StandardCopyOption.ATOMIC_MOVE);
      } catch (IOException e) {
        try {
          abort();
        } catch (IOException ex) {
          e.addSuppressed(ex);
        }
        throw e;
      }
    }

    @Override
    public void close() throws IOException {
      if (closed.compareAndSet(false, true)) abort();
    }

    protected void abort() throws IOException {
      Files.deleteIfExists(tempFile());
    }
  }

  record TransactionalFile(Closeable input, AtomicFileWriter atomicFileWriter) implements Closeable {
    public Path commit() throws IOException {
      return atomicFileWriter.commitWith(input);
    }

    @Override
    public void close() throws IOException {
      atomicFileWriter.close();
    }

    public void deleteIfExists() throws IOException {
      Files.deleteIfExists(atomicFileWriter.finalFileLocation());
    }
  }
}
