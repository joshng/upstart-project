package upstart.util.concurrent;

import upstart.util.collect.Optionals;
import upstart.util.context.Contextualized;
import upstart.util.exceptions.ThrowingConsumer;
import upstart.util.functions.QuadFunction;
import upstart.util.functions.TriFunction;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static com.google.common.base.Strings.lenientFormat;

public class OptionalPromise<T> extends ExtendedPromise<Optional<T>, OptionalPromise<T>> {
  static final PromiseFactory OPTIONAL_PROMISE_FACTORY = PromiseFactory.of(
          OptionalPromise.class,
          Optional.empty(),
          OptionalPromise::new
  );

  public OptionalPromise() {
  }

  public OptionalPromise(CompletableFuture<Contextualized<Optional<T>>> completion) {
    super(completion);
  }

  public static <T> OptionalPromise<T> thatCompletesOptional(ThrowingConsumer<? super OptionalPromise<T>> completion) {
    return OPTIONAL_PROMISE_FACTORY.<Optional<T>, OptionalPromise<T>>thatCompletes(completion);
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
  public static <T> OptionalPromise<T> ofFutureNullable(CompletionStage<T> future) {
    return Promise.of(future).thenApplyOptional(Optional::ofNullable);
  }

  public static <T> OptionalPromise<T> ofFutureOptional(CompletionStage<Optional<T>> stage) {
    CompletableFuture<Optional<T>> future;
    return stage instanceof OptionalPromise<T> already
            ? already
            : CompletableFutures.isCompletedNormally(future = stage.toCompletableFuture())
                    ? completed(future.join())
                    : new OptionalPromise<T>().completeWith(future);
  }

  public static <I, O> OptionalPromise<O> mapToFuture(Optional<I> input, Function<? super I, ? extends CompletionStage<O>> mapper) {
    return input.map(mapper).map(OptionalPromise::ofFutureNullable).orElse(empty());
  }

  public static <I, O> OptionalPromise<O> mapToFutureOptional(Optional<I> input, Function<? super I, ? extends CompletionStage<Optional<O>>> mapper) {
    return input.map(mapper).map(OptionalPromise::ofFutureOptional).orElse(empty());
  }

  public static <T> OptionalPromise<T> toFutureOptional(Optional<? extends CompletionStage<T>> optionalFuture) {
    return optionalFuture.map(f -> Promise.of(f).thenApplyOptional(Optional::ofNullable)).orElse(empty());
  }

  public static <T> ListPromise<T> toFlattenedList(Stream<OptionalPromise<T>> optionalPromiseStream) {
    return CompletableFutures.allAsList(optionalPromiseStream).thenFlatMap(Optional::stream);
  }

  public <O> OptionalPromise<O> thenMap(Function<? super T, ? extends O> mapper) {
    return thenApplyPromise(OPTIONAL_PROMISE_FACTORY, Contextualized.liftFunction(optional -> optional.map(mapper)));
  }

  public <O> OptionalPromise<O> thenFlatMap(Function<? super T, ? extends Optional<O>> mapper) {
    return thenApplyPromise(OPTIONAL_PROMISE_FACTORY, Contextualized.liftFunction(value -> value.flatMap(mapper)));
  }

  public <O> OptionalPromise<O> thenMapCompose(Function<? super T, ? extends CompletionStage<O>> mapper) {
    return thenComposePromise(OPTIONAL_PROMISE_FACTORY, Contextualized.liftAsyncFunction(optional -> mapToFuture(optional, mapper)));
  }

  public <O> OptionalPromise<O> thenFlatMapCompose(Function<? super T, ? extends CompletionStage<Optional<O>>> mapper) {
    return thenComposePromise(OPTIONAL_PROMISE_FACTORY, Contextualized.liftAsyncFunction(optional -> mapToFutureOptional(optional, mapper)));
  }

  public OptionalPromise<T> thenFilter(Predicate<? super T> filter) {
    return thenApplyPromise(OPTIONAL_PROMISE_FACTORY, Contextualized.liftFunction(value -> value.filter(filter)));
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

  /**
   * Requires a message, because debugging an asynchronous NoSuchElementException with no context can be tough.
   */
  public Promise<T> orElseThrow(String message) {
    return orElseThrow(() -> new NoSuchElementException(message));
  }

  /**
   * Requires a message, because debugging an asynchronous NoSuchElementException with no context can be tough.
   */
  public Promise<T> orElseThrow(String format, Object... args) {
    return orElseThrow(() -> new NoSuchElementException(lenientFormat(format, args)));
  }

  public Promise<T> orElseThrow(Supplier<? extends RuntimeException> exceptionSupplier) {
    return thenApply(optional -> optional.orElseThrow(exceptionSupplier));
  }

  public Promise<T> orElseCompose(Supplier<? extends CompletionStage<T>> asyncSupplier) {
    return thenCompose(optional -> optional
            .<CompletionStage<T>>map(CompletableFuture::completedFuture)
            .orElseGet(asyncSupplier));
  }

  public OptionalPromise<T> or(Supplier<? extends Optional<? extends T>> supplier) {
    return thenApplyOptional(value -> value.or(supplier));
  }

  public OptionalPromise<T> orCompose(Supplier<? extends CompletionStage<Optional<T>>> supplier) {
    return thenComposeOptional(value -> value.<CompletionStage<Optional<T>>>map(OptionalPromise::of).orElseGet(supplier));
  }

  public <A, O> OptionalPromise<O> thenMapWith(CompletionStage<A> other, BiFunction<? super T, ? super A, O> mapper) {
    return thenCombinePromise(OPTIONAL_PROMISE_FACTORY, other, Contextualized.liftBiFunction((v1, v2) -> v1.map(v -> mapper.apply(v, v2))));
  }

  public <A, B, O> OptionalPromise<O> thenMapWith(CompletionStage<A> a, CompletionStage<B> b, TriFunction<? super T, ? super A, ? super B, O> mapper) {
    Promise<Optional<O>> combined = combine(
            this,
            a.toCompletableFuture(),
            b.toCompletableFuture(),
            (v1, v2, v3) -> v1.map(v -> mapper.apply(v, v2, v3))
    );
    return ofFutureOptional(combined);
  }

  public <A, B, C, O> OptionalPromise<O> thenMapWith(
          CompletionStage<A> a,
          CompletionStage<B> b,
          CompletionStage<C> c,
          QuadFunction<? super T, ? super A, ? super B, ? super C, O> mapper
  ) {
    Promise<Optional<O>> combined = combine(
            this,
            a.toCompletableFuture(),
            b.toCompletableFuture(),
            c.toCompletableFuture(),
            (v1, v2, v3, v4) -> v1.map(v -> mapper.apply(v, v2, v3, v4))
    );
    return ofFutureOptional(combined);
  }

  public <I, O> OptionalPromise<O> thenZipWith(CompletionStage<? extends Optional<? extends I>> other, BiFunction<? super T, ? super I, O> mapper) {
    return thenCombinePromise(OPTIONAL_PROMISE_FACTORY, other, Contextualized.liftBiFunction((v1, v2) -> Optionals.zip(v1, v2, mapper)));
  }

  public <I, O> OptionalPromise<O> thenFlatZipWith(CompletionStage<? extends Optional<? extends I>> other, BiFunction<? super T, ? super I, Optional<O>> mapper) {
    return thenCombinePromise(OPTIONAL_PROMISE_FACTORY, other, Contextualized.liftBiFunction((v1, v2) -> Optionals.flatZip(v1, v2, mapper)));
  }

  public <A, B, O> OptionalPromise<O> thenFlatZipWith(
          CompletionStage<? extends Optional<? extends A>> a,
          CompletionStage<? extends Optional<? extends B>> b,
          TriFunction<? super T, ? super A, ? super B, Optional<O>> mapper
  ) {
    return ofFutureOptional(combine(
            this,
            a.toCompletableFuture(),
            b.toCompletableFuture(),
            (v1, v2, v3) -> Optionals.flatZip(v1, v2, v3, mapper)
    ));
  }

  public <I, O> OptionalPromise<O> thenMapComposeWith(
          CompletionStage<I> other,
          BiFunction<? super T, ? super I, ? extends CompletionStage<O>> mapper
  ) {
    return ofFutureOptional(CompletableFutures.sequence(thenCombine(
            other,
            (v1, v2) -> toFutureOptional(v1.map(v -> mapper.apply(v, v2)))
    )));
  }

  public <A, B, O> OptionalPromise<O> thenMapComposeWith(
          CompletionStage<A> a,
          CompletionStage<B> b,
          TriFunction<? super T, ? super A, ? super B, ? extends CompletionStage<O>> mapper
  ) {
    return ofFutureOptional(combineCompose(
            this,
            a.toCompletableFuture(),
            b.toCompletableFuture(),
            (opt, aa, bb) -> toFutureOptional(opt.map(v -> mapper.apply(v, aa, bb)))
    ));
  }

  public <A, B, C, O> OptionalPromise<O> thenMapComposeWith(
          CompletionStage<A> a,
          CompletionStage<B> b,
          CompletionStage<C> c,
          QuadFunction<? super T, ? super A, ? super B, ? super C, ? extends CompletionStage<O>> mapper
  ) {
    return ofFutureOptional(combineCompose(
            this,
            a.toCompletableFuture(),
            b.toCompletableFuture(),
            c.toCompletableFuture(),
            (opt, aa, bb, cc) -> toFutureOptional(opt.map(v -> mapper.apply(v, aa, bb, cc))
    )));
  }

  public <I, O> OptionalPromise<O> thenFlatMapWith(
          CompletionStage<I> other,
          BiFunction<? super T, ? super I, ? extends Optional<O>> mapper
  ) {
    return thenCombinePromise(OPTIONAL_PROMISE_FACTORY, other, Contextualized.liftBiFunction((v1, v2) -> v1.flatMap(v -> mapper.apply(v, v2))));
  }

  public <I, O> OptionalPromise<O> thenFlatMapComposeWith(
          CompletionStage<I> other,
          BiFunction<? super T, ? super I, ? extends CompletionStage<Optional<O>>> mapper
  ) {
    return ofFutureOptional(CompletableFutures.sequence(
            thenCombine(other, (v1, v2) -> v1.<CompletionStage<Optional<O>>>map(v -> mapper.apply(v, v2)).orElse(empty()))));
  }

  public <A, B, O> OptionalPromise<O> thenFlatMapComposeWith(
          CompletionStage<A> a,
          CompletionStage<B> b,
          TriFunction<? super T, ? super A, ? super B, ? extends CompletionStage<Optional<O>>> mapper
  ) {
    return ofFutureOptional(combineCompose(
            this,
            a.toCompletableFuture(),
            b.toCompletableFuture(),
            (opt, aa, bb) -> mapToFutureOptional(opt, v -> mapper.apply(v, aa, bb))
    ));
  }

  public <I, O> OptionalPromise<O> thenZipComposeWith(
          CompletionStage<? extends Optional<? extends I>> other,
          BiFunction<? super T, ? super I, ? extends CompletionStage<O>> mapper
  ) {
    return ofFutureOptional(combineCompose(this, other.toCompletableFuture(), (v1, v2) -> toFutureOptional(
            Optionals.zip(v1, v2, mapper)
    )));
  }

  public <I, O> OptionalPromise<O> thenFlatZipComposeWith(
          CompletionStage<? extends Optional<? extends I>> other,
          BiFunction<? super T, ? super I, ? extends CompletionStage<Optional<O>>> mapper
  ) {
    return ofFutureOptional(combineCompose(this, other.toCompletableFuture(), (v1, v2) -> Optionals
            .zip(v1, v2, mapper)
            .map(OptionalPromise::ofFutureOptional)
            .orElse(empty())));
  }

  public boolean completeWithValue(@Nonnull T value) {
    return complete(Optional.of(value));
  }

  public boolean completeWithNullable(@Nullable T value) {
    return complete(Optional.ofNullable(value));
  }

  public boolean completeEmpty() {
    return complete(Optional.empty());
  }

  // TODO so many missing permutations of arity, map/flatMap for both optional and future .. need proper monad transformers and tuples :-(
  // https://medium.com/@johnmcclean/simulating-higher-kinded-types-in-java-b52a18b72c74
  // https://github.com/derive4j/derive4j
  // ... or just use scala, or wait for project loom :-/

  @Override
  protected PromiseFactory sameTypeSubsequentFactory() {
    return OPTIONAL_PROMISE_FACTORY;
  }
}
