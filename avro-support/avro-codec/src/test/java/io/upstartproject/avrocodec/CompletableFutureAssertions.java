package io.upstartproject.avrocodec;

import org.junit.jupiter.api.Assertions;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

public final class CompletableFutureAssertions {

  public static void assertDone(CompletionStage<?> future) {
    assertWithMessage("future.isDone").that(future.toCompletableFuture().isDone()).isTrue();
  }

  public static <E extends Throwable> E assertFailedWith(Class<E> exceptionType, CompletionStage<?> stage) {
    CompletableFuture<?> future = stage.toCompletableFuture();
    assertDone(future);
    assertWithMessage("future.isCompletedExceptionally").that(future.isCompletedExceptionally()).isTrue();
    Throwable thrown = Assertions.assertThrows(CompletionException.class, future::join);
    Throwable cause = thrown.getCause();
    assertThat(cause).isInstanceOf(exceptionType);
    return exceptionType.cast(cause);
  }

  public static <T> T assertCompleted(CompletionStage<T> stage) {
    CompletableFuture<T> f = stage.toCompletableFuture();
    assertDone(f);
    return f.join();
  }

  public static <F extends CompletionStage<?>> F assertPending(F future) {
    assertWithMessage("future.isDone").that(future.toCompletableFuture().isDone()).isFalse();
    return future;
  }
}
