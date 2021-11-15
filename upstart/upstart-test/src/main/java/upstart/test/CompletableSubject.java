package upstart.test;

import com.google.common.truth.Fact;
import com.google.common.truth.FailureMetadata;
import com.google.common.truth.Subject;
import com.google.common.truth.ThrowableSubject;
import com.google.common.truth.Truth;
import org.junit.jupiter.api.Assertions;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

public final class CompletableSubject<T> extends Subject {
  private final CompletableFuture<T> actual;

  public static <T> Subject.Factory<CompletableSubject<T>, CompletableFuture<T>> completables() {
    return CompletableSubject::new;
  }

  public static <T> CompletableSubject<T> assertThat(CompletionStage<T> future) {
    return Truth.assertAbout(CompletableSubject<T>::new).that(future);
  }

  protected CompletableSubject(FailureMetadata metadata, CompletionStage<T> actual) {
    super(metadata, actual);
    this.actual = actual.toCompletableFuture();
  }

  public void isDone() {
    check("isDone()").that(actual.isDone()).isTrue();
  }

  public void isNotDone() {
    if (actual.isDone()) failWithActual(Fact.simpleFact("expected incomplete future"));
  }

  public void isCompletedExceptionally() {
    check("isCompletedExceptionally()").that(actual.isCompletedExceptionally()).isTrue();
  }

  public void isNotCompletedExceptionally() {
    check("isCompletedExceptionally()").that(actual.isCompletedExceptionally()).isFalse();
  }

  public void isCancelled() {
    check("isCancelled()").that(actual.isCancelled()).isTrue();
  }

  public T getCompletedResult() {
    isDone();
    return actual.join(); // will throw if completed exceptionally
  }

  public ThrowableSubject isFailedWithExceptionThat() {
    return Truth.assertThat(getCompletedException());
  }

  public ThrowableSubject willFailWithExceptionThat(Duration timeout) {
    return Truth.assertThat(willFail(timeout));
  }

  public <E extends Throwable> E getCompletedException(Class<E> expectedExceptionType) {
    Throwable e = getCompletedException();
    Truth.assertThat(e).isInstanceOf(expectedExceptionType);
    return expectedExceptionType.cast(getCompletedException());
  }

  public Throwable getCompletedException() {
    isCompletedExceptionally();
    return willFail(Duration.ZERO);
  }

  public <E extends Throwable> E willFail(Class<E> expectedExceptionType, Duration timeout) {
    Truth.assertThat(willFail(timeout)).isInstanceOf(expectedExceptionType);
    return expectedExceptionType.cast(willFail(timeout));
  }

  public Throwable willFail(Duration timeout) {
    ExecutionException thrown = Assertions.assertThrows(ExecutionException.class, () -> actual.get(timeout.toNanos(), TimeUnit.NANOSECONDS));
    return thrown.getCause();
  }
}
