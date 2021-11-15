package upstart.test;

import com.google.common.truth.Fact;
import com.google.common.truth.FailureMetadata;
import com.google.common.truth.Subject;
import com.google.common.truth.Truth;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.concurrent.CompletableFuture;

public class CompletableFutureSubject<T> extends Subject {
  private final CompletableFuture<T> actual;

  public static Factory<CompletableFutureSubject<Object>, CompletableFuture<Object>> completableFutures() {
    return CompletableFutureSubject::new;
  }

  @SuppressWarnings("unchecked")
  public static <T> CompletableFutureSubject<T> assertThat(CompletableFuture<T> path) {
    return (CompletableFutureSubject<T>) Truth.assertAbout(completableFutures()).that((CompletableFuture<Object>) path);
  }

  protected CompletableFutureSubject(FailureMetadata metadata, @Nullable CompletableFuture<T> actual) {
    super(metadata, actual);
    this.actual = actual;
  }

  public void isDone() {
    check(actual.isDone(), "expected to be done");
  }

  public <S extends Subject, F extends Factory<S, T>> S hasResultThat(F factory) {
    isDone();
    return Truth.assertAbout(factory).that(actual.join());
  }

  private void check(boolean condition, String failureMessage) {
    if (!condition) failWithoutActual(Fact.fact("CompletableFuture", failureMessage));
  }

}
