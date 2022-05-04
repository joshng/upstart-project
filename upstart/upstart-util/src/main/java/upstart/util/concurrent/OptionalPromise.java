package upstart.util.concurrent;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class OptionalPromise<T> extends AbstractPromise<Optional<T>, OptionalPromise<T>> {
  @SuppressWarnings("rawtypes")
  private static final OptionalPromise EMPTY = new OptionalPromise<>().fulfill(Optional.empty());

  @SuppressWarnings("unchecked")
  public static <T> OptionalPromise<T> empty() {
    return EMPTY;
  }

  public static <T> OptionalPromise<T> of(@Nonnull Optional<T> optional) {
    return optional.isEmpty() ? empty() : new OptionalPromise<T>().fulfill(optional);
  }

  public static <T> OptionalPromise<T> present(@Nonnull T value) {
    return new OptionalPromise<T>().fulfill(Optional.of(value));
  }

  public static <T> OptionalPromise<T> completedNullable(@Nullable T value) {
    return value == null ? empty() : present(value);
  }
  public static <T> OptionalPromise<T> ofFutureNullable(CompletionStage<T> future) {
    return ofFutureOptional(future.thenApply(Optional::ofNullable));
  }

  public static <T> OptionalPromise<T> ofFutureOptional(CompletionStage<Optional<T>> future) {
    return future instanceof OptionalPromise<T> already ? already : new OptionalPromise<T>().completeWith(future);
  }

  public static <T> OptionalPromise<T> toFutureOptional(Optional<? extends CompletionStage<T>> optionalFuture) {
    return ofFutureOptional(optionalFuture.map(f -> f.thenApply(Optional::ofNullable).toCompletableFuture()).orElse(empty()));
  }

  public <O> OptionalPromise<O> thenMap(Function<? super T, ? extends O> mapper) {
    return asOptionalPromise(() -> baseApply(value -> value.map(mapper)));
  }

  public <O> OptionalPromise<O> thenMapCompose(Function<? super T, ? extends CompletionStage<O>> mapper) {
    return asOptionalPromise(() -> baseCompose(value -> toFutureOptional(value.map(mapper))));
  }

  public <O> OptionalPromise<O> thenFlatMapCompose(Function<? super T, ? extends CompletionStage<Optional<O>>> mapper) {
    return asOptionalPromise(() -> baseCompose(value -> value.<CompletionStage<Optional<O>>>map(mapper)
            .orElse(OptionalPromise.empty())));
  }

  public <O> OptionalPromise<O> thenFlatMap(Function<? super T, ? extends Optional<O>> mapper) {
    return asOptionalPromise(() -> baseApply(value -> value.flatMap(mapper)));
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

  @Override
  protected PromiseFactory<OptionalPromise<T>> factory() {
    return OptionalPromise::new;
  }
}