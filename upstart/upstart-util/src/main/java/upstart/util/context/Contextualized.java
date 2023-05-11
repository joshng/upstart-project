package upstart.util.context;

import upstart.util.collect.Entries;
import upstart.util.concurrent.Promise;
import upstart.util.exceptions.Try;
import upstart.util.functions.AsyncFunction;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class Contextualized<T> implements TransientContext {
  private final Try<T> value;
  private final AsyncContext snapshot;

  public Contextualized(Try<? extends T> value, AsyncContext contextSnapshot) {
    //noinspection unchecked
    this.value = (Try<T>) value;
    this.snapshot = contextSnapshot;
  }

  public static <T> Contextualized<T> value(T value) {
    return of(value, AsyncContext.snapshot());
  }

  public static <T> Contextualized<T> ofTry(Try<? extends T> value) {
    return new Contextualized<>(value, AsyncContext.snapshot());
  }

  public static <T> Contextualized<T> failure(Throwable failure) {
    return ofTry(Try.failure(failure));
  }

  public static <T> Contextualized<T> canceled() {
    return ofTry(Try.failure(new CancellationException()));
  }

  public static <T> Contextualized<T> of(T value, AsyncContext context) {
    return new Contextualized<>(Try.success(value), context);
  }

  public static <T, U> ContextualFunction<T, U> liftFunction(Function<? super T, ? extends U> fn) {
    return (Contextualized<T> ctx) -> ctx.map(fn);
  }

  public static <T> ContextualFunction<T, Void> liftRunnable(Runnable fn) {
    return liftFunction(ignored -> {
      fn.run();
      return null;
    });
  }

  public static <I, T> ContextualFunction<I, T> liftSupplier(Supplier<T> fn) {
    return liftFunction(ignored -> fn.get());
  }

  public static <T> ContextualFunction<T, Void> liftConsumer(Consumer<? super T> fn) {
    return ctx -> ctx.map(value -> {
      fn.accept(value);
      return null;
    });
  }

  public static <T, U, V> BiFunction<Contextualized<T>, Contextualized<? extends U>, Contextualized<V>> liftBiFunction(
          BiFunction<? super T, ? super U, ? extends V> fn
  ) {
    return (ctx1, ctx2) -> ctx1.combine(ctx2, fn);
  }

  public static <T, U> ContextualAsyncFunction<T, U> liftAsyncFunction(Function<? super T, ? extends CompletionStage<U>> fn) {
    return ctx -> ctx.mapAysnc(fn);
  }

  public static <T, U> Function<Contextualized<T>, Contextualized<U>> liftHandlerFunction(BiFunction<? super T, Throwable, ? extends U> fn) {
    return ctx -> ctx.mapTry(attempt -> attempt.handle(fn));
  }

  public static <T> Function<Contextualized<T>, Contextualized<T>> liftRecoverFunction(Function<Throwable, ? extends T> fn) {
    return ctx -> ctx.recover(fn);
  }

  public Try<T> value() {
    return value;
  }

  public AsyncContext contextSnapshot() {
    return snapshot;
  }

  public Contextualized<T> mergeFrom(AsyncContext context) {
    return context.isEmpty() || context == this.snapshot
            ? this
            : new Contextualized<>(value, this.snapshot.mergeFrom(context));
  }

  public Contextualized<T> withFallback(AsyncContext current) {
    return current.isEmpty() || current == snapshot ? this : new Contextualized<>(value, current.mergeFrom(snapshot));
  }

  public static Contextualized<Void> mergeContexts(Contextualized<Void> a, Contextualized<Void> b) {
    return b.value().isFailure()
            ? b.mergeFrom(a.contextSnapshot())
            : a.mergeFrom(b.contextSnapshot());
  }


  @Override
  public State open() {
    return snapshot.open();
  }

  public <U> Contextualized<U> map(Function<? super T, ? extends U> fn) {
    try (State ignored = open()) {
      return ofTry(value.map(fn));
    }
  }

  public <U> Contextualized<U> mapTry(Function<? super Try<T>, Try<U>> fn) {
    try (State ignored = open()) {
      return ofTry(fn.apply(value));
    }
  }

  public <U, V> Contextualized<V> combine(Contextualized<U> other, BiFunction<? super T, ? super U, ? extends V> fn) {
    try (State ignored = open()) {
      return other.mapTry(t2 -> value.combine(t2).map(Entries.tupled(fn)));
    }
  }

  public Contextualized<T> recover(Function<? super Throwable, ? extends T> fn) {
    if (value.isSuccess()) return this;
    try (State ignored = open()) {
      return ofTry(value.recover(fn));
    }
  }

  public <U> CompletionStage<Contextualized<U>> mapAysnc(Function<? super T, ? extends CompletionStage<U>> fn) {
    try (State ignored = open()) {
      return value.map(fn.andThen(ContextualizedFuture::captureContext))
              .recover(t -> ContextualizedFuture.completed(Contextualized.failure(t)))
              .get();
    }
  }

  public static <T> Function<Contextualized<T>, CompletionStage<Contextualized<T>>> liftAsyncRecoverFunction(
          Function<? super Throwable, ? extends CompletionStage<T>> fn
  ) {
    return ctx -> Contextualized.propagateContext(ctx.map(CompletableFuture::completedStage).recover(fn));
  }

  public static <T> CompletionStage<Contextualized<T>> propagateContext(Contextualized<? extends CompletionStage<T>> cf) {
    CompletionStage<T> completion = cf.value().get();
    return completion instanceof Promise<T> promise
            ? promise.contextualizedFuture()
            : completion.thenApply(result -> Contextualized.of(result, AsyncContext.snapshot().mergeFrom(cf.contextSnapshot())));
  }

  public void accept(BiConsumer<? super T, ? super Throwable> action) {
    runInContext(() -> value.accept(action));
  }

  public interface ContextualFunction<I, O> extends Function<Contextualized<I>, Contextualized<O>> {

  }
  public interface ContextualAsyncFunction<I, O> extends AsyncFunction<Contextualized<I>, Contextualized<O>> {

  }
}
