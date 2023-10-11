package upstart.util.concurrent;

import com.google.common.collect.Iterables;
import upstart.util.collect.MoreStreams;
import upstart.util.context.Contextualized;
import upstart.util.exceptions.ThrowingConsumer;

import javax.annotation.Nonnull;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class ListPromise<T> extends ExtendedPromise<List<T>, ListPromise<T>> {
  static final PromiseFactory LIST_PROMISE_FACTORY = PromiseFactory.of(ListPromise.class, List.of(), ListPromise::new);

  public ListPromise() {
  }

  private ListPromise(CompletableFuture<Contextualized<List<T>>> completion) {
    super(completion);
  }

  public static <T> ListPromise<T> empty() {
    return LIST_PROMISE_FACTORY.emptyInstance();
  }

  public static <T> ListPromise<T> canceled() {
    return LIST_PROMISE_FACTORY.canceledInstance();
  }

  public static <T> ListPromise<T> thatCompletesList(ThrowingConsumer<? super ListPromise<T>> completion) {
    return LIST_PROMISE_FACTORY.<List<T>, ListPromise<T>>thatCompletes(completion);
  }

  @SafeVarargs
  public static <T> ListPromise<T> asListPromise(T... values) {
    return completed(Arrays.asList(values));
  }

  public static <T> Collector<CompletableFuture<? extends T>, ?, ListPromise<T>> toListPromise() {
    return Collectors.collectingAndThen(Collectors.toList(), ListPromise::allAsList);
  }

  public static <T> ListPromise<T> completed(@Nonnull List<T> list) {
    return list.isEmpty() ? empty() : new ListPromise<T>().fulfill(list);
  }

  @SafeVarargs
  public static <T> ListPromise<T> allAsList(CompletableFuture<? extends T>... array) {
    return array.length == 0
            ? empty()
            : allOf(array).thenStreamToList(ignored -> Stream.of(array).map(CompletableFuture::join));
  }

  @SuppressWarnings("unchecked")
  public static <T> ListPromise<T> allAsList(Stream<? extends CompletableFuture<? extends T>> futures) {
    return futures.collect(toListPromise());
  }

  @SuppressWarnings("unchecked")
  public static <T> ListPromise<T> allAsList(Collection<? extends CompletableFuture<? extends T>> futures) {
    return allAsList(futures.toArray(CompletableFuture[]::new));
  }

  public ListPromise<T> distinct() {
    return thenApplyList(list -> list.stream().distinct().toList());
  }

  public ListPromise<T> distinctBy(Function<? super T, ?> keyExtractor) {
    return thenApplyList(list -> list.stream().collect(Collectors.toMap(keyExtractor, Function.identity())).values().stream().toList());
  }

  public OptionalPromise<T> toOptionalOnlyElement() {
    return thenApplyOptional(list -> list.isEmpty() ? Optional.empty() : Optional.of(Iterables.getOnlyElement(list)));
  }

  public <O> Promise<O> thenCollect(Collector<? super T, ?, O> collector) {
    return thenApply(list -> list.stream().collect(collector));
  }

  public static <T> ListPromise<T> ofFutureList(CompletableFuture<List<T>> future) {
    return future instanceof ListPromise<T> already
            ? already
            : CompletableFutures.isCompletedNormally(future)
                    ? completed(future.join())
                    : new ListPromise<T>().completeWith(future);
  }

  @Deprecated
  public static <T> ListPromise<T> ofFutureList(ListPromise<T> already) {
    return already;
  }

  public <O> ListPromise<O> thenMap(Function<? super T, O> mapper) {
    return thenApplyPromise(LIST_PROMISE_FACTORY, Contextualized.liftFunction(value -> value.stream().map(mapper).toList()));
  }

  public <O> ListPromise<O> thenFlatMap(Function<? super T, ? extends Stream<O>> mapper) {
    return thenApplyPromise(LIST_PROMISE_FACTORY, Contextualized.liftFunction(value -> value.stream().flatMap(mapper).toList()));
  }

  public <O> ListPromise<O> thenMapCompose(Function<? super T, ? extends CompletableFuture<O>> mapper) {
    return thenComposePromise(
            LIST_PROMISE_FACTORY,
            Contextualized.liftAsyncFunction(value -> value.stream().map(mapper).collect(toListPromise()))
    );
  }

  public <O> ListPromise<O> thenFlatMapCompose(Function<? super T, ? extends CompletableFuture<List<O>>> mapper) {
    return thenComposePromise(LIST_PROMISE_FACTORY, Contextualized.liftAsyncFunction(value -> value.stream()
            .map(mapper)
            .collect(toListPromise())
            .thenApply(lists -> lists.stream().flatMap(List::stream).toList())));
  }

  public ListPromise<T> thenFilter(Predicate<? super T> filter) {
    return thenApplyPromise(LIST_PROMISE_FACTORY, Contextualized.liftFunction(value -> value.stream().filter(filter).toList()));
  }

  public <V> ListPromise<V> thenFilter(Class<V> filterClass) {
    return thenApplyPromise(LIST_PROMISE_FACTORY, Contextualized.liftFunction(value -> value.stream()
            .filter(filterClass::isInstance).map(filterClass::cast).toList()));
  }

  public <O> Promise<O> thenFoldLeft(O identity, BiFunction<? super O, ? super T, ? extends O> folder) {
    return thenApply(list -> MoreStreams.foldLeft(identity, list.stream(), folder));
  }

  @Override
  protected PromiseFactory sameTypeSubsequentFactory() {
    return LIST_PROMISE_FACTORY;
  }
}
