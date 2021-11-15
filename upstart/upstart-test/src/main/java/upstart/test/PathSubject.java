package upstart.test;

import com.google.common.truth.Fact;
import com.google.common.truth.FailureMetadata;
import com.google.common.truth.Subject;
import com.google.common.truth.Truth;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class PathSubject extends Subject {
  private final Path actual;

  public static Factory<PathSubject, Path> paths() {
    return PathSubject::new;
  }

  public static PathSubject assertThat(Path path) {
    return Truth.assertAbout(paths()).that(path);
  }

  public static PathSubject assertThatPath(Path path) {
    return assertThat(path);
  }

  protected PathSubject(FailureMetadata metadata, Path actual) {
    super(metadata, actual);
    this.actual = actual;
  }

  public void exists() {
    check(Files.exists(actual), "expected to exist");
  }

  public void containsExactly(String string) throws IOException {
    exists();
    Truth.assertThat(Files.readString(actual)).isEqualTo(string);
  }

  private void check(boolean condition, String failureMessage) {

    if (!condition) failWithoutActual(Fact.fact("Path " + actual, failureMessage));
  }

}
