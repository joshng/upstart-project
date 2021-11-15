package upstart.util;

import com.google.common.base.Preconditions;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Predicate;

import static com.google.common.base.Preconditions.checkState;

public class MorePaths {
  public static Path ensureWritableDir(Path path) throws IOException {
    Files.createDirectories(path);
    checkState(Files.isWritable(path), "Cannot write to directory %s", path);
    return path;
  }

  public static <T> T checkThat(T value, Predicate<? super T> condition, String message, Object... args) {
    Preconditions.checkState(condition.test(value), message, args);
    return value;
  }
}
