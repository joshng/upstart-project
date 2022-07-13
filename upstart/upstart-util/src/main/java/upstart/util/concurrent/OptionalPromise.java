package upstart.util.concurrent;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class OptionalPromise<T> extends ExtendedPromise<Optional<T>, OptionalPromise<T>> {
  @SuppressWarnings("unchecked")
  public static <T> OptionalPromise<T> empty() {
    return (OptionalPromise<T>) EmptyPromise.INSTANCE;
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
  public static <T> OptionalPromise<T> ofFutureNullable(CompletionStage<T> future) {
    return Promise.of(future).thenApplyOptional(Optional::ofNullable);
  }

  public static <I, O> OptionalPromise<O> mapToPromise(Optional<I> input, Function<? super I, ? extends CompletionStage<O>> mapper) {
    return input.map(mapper).map(OptionalPromise::ofFutureNullable).orElse(empty());
  }

  public static <I, O> OptionalPromise<O> flatMapToPromise(Optional<I> input, Function<? super I, ? extends CompletionStage<Optional<O>>> mapper) {
    return input.map(mapper).map(OptionalPromise::ofFutureOptional).orElse(empty());
  }

  public static <T> OptionalPromise<T> ofFutureOptional(CompletionStage<Optional<T>> stage) {
    CompletableFuture<Optional<T>> future;
    return stage instanceof OptionalPromise<T> already
            ? already
            : CompletableFutures.isCompletedNormally(future = stage.toCompletableFuture())
                    ? completed(future.join())
                    : new OptionalPromise<T>().completeWith(future);
  }

  public static <T> OptionalPromise<T> toFutureOptional(Optional<? extends CompletionStage<T>> optionalFuture) {
    return optionalFuture.map(f -> Promise.of(f).thenApplyOptional(Optional::ofNullable)).orElse(empty());
  }

  public <O> OptionalPromise<O> thenMap(Function<? super T, ? extends O> mapper) {
    return asOptionalPromise(() -> baseApply(value -> value.map(mapper)));
  }

  public <O> OptionalPromise<O> thenFlatMap(Function<? super T, ? extends Optional<O>> mapper) {
    return asOptionalPromise(() -> baseApply(value -> value.flatMap(mapper)));
  }

  public <O> OptionalPromise<O> thenMapCompose(Function<? super T, ? extends CompletionStage<O>> mapper) {
    return asOptionalPromise(() -> baseCompose(value -> toFutureOptional(value.map(mapper))));
  }

  public <O> OptionalPromise<O> thenFlatMapCompose(Function<? super T, ? extends CompletionStage<Optional<O>>> mapper) {
    return asOptionalPromise(() -> baseCompose(value -> value.<CompletionStage<Optional<O>>>map(mapper)
            .orElse(OptionalPromise.empty())));
  }

  public OptionalPromise<T> thenFilter(Predicate<? super T> filter) {
    return asOptionalPromise(() -> baseApply(value -> value.filter(filter)));
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

  public OptionalPromise<T> or(Supplier<? extends CompletionStage<Optional<T>>> supplier) {
    return asOptionalPromise(() -> baseCompose(value -> value.<CompletionStage<Optional<T>>>map(OptionalPromise::of).orElseGet(supplier)));
  }


  public <I, O> OptionalPromise<O> thenMapCombine(CompletionStage<I> other, BiFunction<? super T, ? super I, O> mapper) {
    return asOptionalPromise(() -> thenCombine(other, (v1, v2) -> v1.map(v -> mapper.apply(v, v2))));
  }

  public <I, O> OptionalPromise<O> thenMapCombinedFuture(
          CompletionStage<I> other,
          BiFunction<? super T, ? super I, ? extends CompletionStage<O>> mapper
  ) {
    return ofFutureOptional(CompletableFutures.sequence(thenCombine(
            other,
            (v1, v2) -> toFutureOptional(v1.map(v -> mapper.apply(v, v2)))
    )));
  }

  public <I, O> OptionalPromise<O> thenFlatMapCombine(
          CompletionStage<I> other,
          BiFunction<? super T, ? super I, ? extends Optional<O>> mapper
  ) {
    return asOptionalPromise(() -> thenCombine(other, (v1, v2) -> v1.flatMap(v -> mapper.apply(v, v2))));
  }

  public <I, O> OptionalPromise<O> thenFlatMapCombinedFuture(
          CompletionStage<I> other,
          BiFunction<? super T, ? super I, ? extends CompletionStage<Optional<O>>> mapper
  ) {
    return ofFutureOptional(CompletableFutures
                                    .sequence(thenCombine(other, (v1, v2) -> v1
                                            .<CompletionStage<Optional<O>>>map(v -> mapper.apply(v, v2)).orElse(empty()))));
  }

  @Override
  protected Promise.PromiseFactory<OptionalPromise<T>> factory() {
    return OptionalPromise::new;
  }

  private static class EmptyPromise extends OptionalPromise<Object> {
    static final EmptyPromise INSTANCE = new EmptyPromise();

    EmptyPromise() {
      fulfill(Optional.empty());
    }

    @Override
    public <O> OptionalPromise<O> thenMap(Function<? super Object, ? extends O> mapper) {
      return empty();
    }

    @Override
    public <O> OptionalPromise<O> thenMapCompose(Function<? super Object, ? extends CompletionStage<O>> mapper) {
      return empty();
    }

    @Override
    public <O> OptionalPromise<O> thenFlatMapCompose(Function<? super Object, ? extends CompletionStage<Optional<O>>> mapper) {
      return empty();
    }

    @Override
    public <O> OptionalPromise<O> thenFlatMap(Function<? super Object, ? extends Optional<O>> mapper) {
      return empty();
    }

    @Override
    public OptionalPromise<Object> thenFilter(Predicate<? super Object> filter) {
      return this;
    }
  }
}
