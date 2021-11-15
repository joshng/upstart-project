package upstart.b4.incremental;

import com.google.inject.assistedinject.Assisted;

import javax.inject.Inject;
import java.nio.file.FileSystem;
import java.nio.file.PathMatcher;
import java.util.Objects;

public class Glob {
  private final String glob;
  private final FileSystem fileSystem;

  @Inject
  public Glob(@Assisted String glob, FileSystem fileSystem) {
    this.glob = Objects.requireNonNull(glob, "glob");
    this.fileSystem = fileSystem;
  }

  public String glob() {
    return glob;
  }

  public PathMatcher pathMatcher() {
    return fileSystem.getPathMatcher("glob:" + glob());
  }

  public interface Factory {
    Glob glob(String glob);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    Glob glob1 = (Glob) o;

    return glob.equals(glob1.glob);
  }

  @Override
  public int hashCode() {
    return glob.hashCode();
  }
}
