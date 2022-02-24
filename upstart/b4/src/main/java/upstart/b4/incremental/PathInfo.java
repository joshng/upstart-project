package upstart.b4.incremental;

import upstart.util.annotations.Tuple;
import upstart.util.exceptions.UncheckedIO;
import org.immutables.value.Value;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.Comparator;

// we could intern/cache these to optimize attr-reading IO, but then info for rebuilt files wouldn't be updated
// TODO: could cache and provide a refresh/expire solution
@Value.Immutable //(intern = true)
//  @Value.Style(weakInterning = true)
@Tuple
public abstract class PathInfo {

  public static Comparator<PathInfo> LAST_MODIFIED_COMPARATOR = Comparator.comparing(PathInfo::lastModifiedTime);

  public static PathInfo of(Path path) {
    return of(path, UncheckedIO.getUnchecked(() -> Files.readAttributes(path, BasicFileAttributes.class)));
  }

  private static PathInfo of(Path path, BasicFileAttributes attrs) {
    return ImmutablePathInfo.of(UncheckedIO.getUnchecked(path::toRealPath), attrs);
  }

  public abstract Path path();

  public abstract BasicFileAttributes attributes();

  public FileTime lastModifiedTime() {
    return attributes().lastModifiedTime();
  }

  public boolean isRegularFile() {
    return attributes().isRegularFile();
  }

  public boolean isAsNewAs(FileTime time) {
    return lastModifiedTime().compareTo(time) >= 0;
  }
}
