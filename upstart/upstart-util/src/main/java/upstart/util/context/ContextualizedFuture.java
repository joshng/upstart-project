package upstart.util.context;

import upstart.util.concurrent.Promise;
import upstart.util.exceptions.Try;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.BiConsumer;

public class ContextualizedFuture<T> extends CompletableFuture<Contextualized<T>> {
  public ContextualizedFuture() {
  }

  private ContextualizedFuture(ObservableCompletion<T> completion) {
    AsyncContext creationContext = AsyncContext.snapshot();
    completion.whenComplete((v, e) -> complete(new Contextualized<>(
            Try.of(v, e),
            creationContext.mergeFrom(AsyncContext.snapshot())
    )));
  }

  public static <T> CompletableFuture<Contextualized<T>> captureContext(CompletionStage<T> value) {
    return value instanceof Promise<T> promise
            ? promise.contextualizedFuture()
            : ContextualizedFuture.<T>contextualizeResult(value::whenComplete);
  }

  public static <T> CompletableFuture<Contextualized<T>> of(T value, AsyncContext context) {
    return completed(new Contextualized<>(Try.success(value), context));
  }

  public static <T> CompletableFuture<Contextualized<T>> completed(Contextualized<T> result) {
    return CompletableFuture.completedFuture(result);
  }

  public static <T> ContextualizedFuture<T> contextualizeResult(ObservableCompletion<T> whenComplete) {
    return new ContextualizedFuture<>(whenComplete);
  }

  @FunctionalInterface
  public interface ObservableCompletion<T> {
    void whenComplete(BiConsumer<? super T, ? super Throwable> action);
  }
}
