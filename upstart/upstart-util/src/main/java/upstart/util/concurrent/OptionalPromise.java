package upstart.util.concurrent;

import upstart.util.context.Contextualized;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

public class OptionalPromise<T> extends ExtendedPromise<Optional<T>, OptionalPromise<T>> {
  static final PromiseFactory OPTIONAL_PROMISE_FACTORY = PromiseFactory.of(Optional.empty(), OptionalPromise::new);

  public OptionalPromise() {
  }

  public OptionalPromise(CompletableFuture<Contextualized<Optional<T>>> completion) {
    super(completion);
  }

  public static <T> OptionalPromise<T> empty() {
    return OPTIONAL_PROMISE_FACTORY.emptyInstance();
  }

  public static <T> OptionalPromise<T> canceled() {
    return OPTIONAL_PROMISE_FACTORY.canceledInstance();
  }

  public static <T> OptionalPromise<T> completed(@Nonnull Optional<T> optional) {
    return optional.isEmpty() ? empty() : new OptionalPromise<T>().fulfill(optional);
  }

  public static <T> OptionalPromise<T> of(@Nonnull T value) {
    return new OptionalPromise<T>().fulfill(Optional.of(value));
  }

  public static <T> OptionalPromise<T> ofNullable(@Nullable T value) {
    return value == null ? empty() : of(value);
  }
  public static <T> OptionalPromise<T> ofFutureNullable(CompletableFuture<T> future) {
    return Promise.of(future).thenApplyOptional(Optional::ofNullable);
  }

  public static <T> OptionalPromise<T> ofFutureOptional(CompletableFuture<Optional<T>> future) {
    return future instanceof OptionalPromise<T> already
            ? already
            : CompletableFutures.isCompletedNormally(future)
                    ? completed(future.join())
                    : new OptionalPromise<T>().completeWith(future);
  }

  public static <T> OptionalPromise<T> toFutureOptional(Optional<? extends CompletionStage<T>> optionalFuture) {
    return optionalFuture.map(f -> Promise.of(f).thenApplyOptional(Optional::ofNullable)).orElse(empty());
  }

  public static <T> ListPromise<T> toFlattenedList(Stream<OptionalPromise<T>> optionalPromiseStream) {
    return CompletableFutures.allAsList(optionalPromiseStream).thenFlatMap(Optional::stream);
  }

  public <O> OptionalPromise<O> thenMap(Function<? super T, ? extends O> mapper) {
    return asOptionalPromise(() -> thenApply(optional -> optional.map(mapper)));
  }

  public <O> OptionalPromise<O> thenMapCompose(Function<? super T, ? extends CompletionStage<O>> mapper) {
    return asOptionalPromise(() -> thenCompose(value -> toFutureOptional(value.map(mapper))));
  }

  public <O> OptionalPromise<O> thenFlatMapCompose(Function<? super T, ? extends CompletionStage<Optional<O>>> mapper) {
    return asOptionalPromise(() -> thenCompose(value -> value.<CompletionStage<Optional<O>>>map(mapper)
            .orElse(OptionalPromise.empty())));
  }

  public <O> OptionalPromise<O> thenFlatMap(Function<? super T, ? extends Optional<O>> mapper) {
    return asOptionalPromise(() -> thenApply(value -> value.flatMap(mapper)));
  }

  public OptionalPromise<T> thenFilter(Predicate<? super T> filter) {
    return asOptionalPromise(() -> thenApply(value -> value.filter(filter)));
  }

  public OptionalPromise<T> thenIfPresent(Consumer<? super T> consumer) {
    return thenApplyOptional(value -> {
      value.ifPresent(consumer);
      return value;
    });
  }

  public Promise<T> orElse(T value) {
    return thenApply(optional -> optional.orElse(value));
  }

  public Promise<T> orElseGet(Supplier<? extends T> supplier) {
    return thenApply(optional -> optional.orElseGet(supplier));
  }

  public Promise<T> orElseThrow() {
    return thenApply(Optional::orElseThrow);
  }

  public Promise<T> orElseThrow(Supplier<? extends RuntimeException> exceptionSupplier) {
    return thenApply(optional -> optional.orElseThrow(exceptionSupplier));
  }

  public Promise<T> orElseCompose(Supplier<? extends CompletionStage<T>> asyncSupplier) {
    return thenCompose(optional -> optional
            .<CompletionStage<T>>map(CompletableFuture::completedFuture)
            .orElseGet(asyncSupplier));
  }

  @Override
  protected Promise.PromiseFactory factory() {
    return OPTIONAL_PROMISE_FACTORY;
  }

}
