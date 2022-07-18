package upstart.util.context;

import upstart.util.concurrent.Promise;
import upstart.util.exceptions.Try;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class ContextualizedFuture<T> extends CompletableFuture<Contextualized<T>> {
  public ContextualizedFuture() {
  }

  private ContextualizedFuture(Consumer<BiConsumer<? super T, ? super Throwable>> whenComplete) {
    AsyncContext.Snapshot creationContext = AsyncContext.snapshot();
    whenComplete.accept((v, e) -> complete(new Contextualized<>(
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

  public static <T> ContextualizedFuture<T> contextualizeResult(Consumer<BiConsumer<? super T, ? super Throwable>> whenComplete) {
    return new ContextualizedFuture<>(whenComplete);
  }
}
