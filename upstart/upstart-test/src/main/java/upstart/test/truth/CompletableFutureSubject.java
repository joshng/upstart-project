package upstart.test.truth;

import com.google.common.truth.CustomSubjectBuilder;
import com.google.common.truth.Fact;
import com.google.common.truth.FailureMetadata;
import com.google.common.truth.Subject;
import com.google.common.truth.ThrowableSubject;
import com.google.common.truth.Truth;
import upstart.util.concurrent.CompletableFutures;
import upstart.util.concurrent.Deadline;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public class CompletableFutureSubject<T> extends Subject {
  private final CompletableFuture<T> actual;

  public static <T> Factory<CompletableFutureSubject<T>, CompletableFuture<T>> completableFutures() {
    return CompletableFutureSubject::new;
  }

  public static <T> CompletableFutureSubject<T> assertThat(CompletableFuture<T> path) {
    return Truth.assertAbout(CompletableFutureSubject.<T>completableFutures()).that(path);
  }

  protected CompletableFutureSubject(FailureMetadata metadata, CompletableFuture<T> actual) {
    super(metadata, actual);
    this.actual = actual;
  }

  public void isDone() {
    check("isDone()").that(actual.isDone()).isTrue();
  }

  public void isNotDone() {
    check("isDone()").that(actual.isDone()).isFalse();
  }

  public CompletableFutureSubject<T> doneWithin(Deadline deadline) throws InterruptedException {
    Duration remaining = deadline.remaining();
    if (!deadline.awaitDone(actual)) {
      failWithoutActual(Fact.fact("completableFuture", "expected to be done within " + remaining));
    }
    return this;
  }

  public T completedNormally() {
    isDone();
    check("exceptionalCompletion").that(CompletableFutures.getException(actual).orElse(null)).isNull();
    return actual.join();
  }

  public Subject havingResultThat() {
    isDone();
    return check("join()").that(actual.join());
  }

  public <S extends Subject> S completedWithResultSatisfying(Function<? super T, S> assertThat) {
    isDone();
    return check("join()").about((metadata, actual1) -> assertThat.apply((T) actual1)).that(actual.join());
  }
  public <S extends CustomSubjectBuilder> S completedWithResult(CustomSubjectBuilder.Factory<S> assertThat) {
    isDone();
    return check("join()").about(assertThat);
  }

  public <S extends Subject, F extends Factory<S, ? super T>> S havingResultThat(F factory) {
    isDone();
    return check("join()").about((Factory<S, ? super T>)factory).that(actual.join());
  }

  public ThrowableSubject completedWithExceptionThat() {
    return Truth.assertThat(failedWith(Throwable.class));
  }

  public <E extends Throwable> E completedExceptionallyWith(Class<E> exceptionType) {
    isDone();
    Optional<Throwable> thrown = CompletableFutures.getException(actual);
    check("isCompletedExceptionally()").that(thrown.isPresent()).isTrue();
    Throwable exception = thrown.get();
    check("exceptionalCompletion").that(exception).isInstanceOf(exceptionType);
    return exceptionType.cast(exception);
  }

  public <E extends Throwable> E failedWith(Class<E> exceptionType) {
    return completedExceptionallyWith(exceptionType);
  }
}
