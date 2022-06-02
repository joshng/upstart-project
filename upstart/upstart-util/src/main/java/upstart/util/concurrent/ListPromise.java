package upstart.util.concurrent;

import upstart.util.collect.MoreStreams;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ListPromise<T> extends AbstractPromise<List<T>, ListPromise<T>> {
  @SuppressWarnings("rawtypes")
  private static final ListPromise EMPTY = new ListPromise<>().fulfill(List.of());

  @SuppressWarnings("unchecked")
  public static <T> ListPromise<T> empty() {
    return EMPTY;
  }

  public static <T> ListPromise<T> allAsList(CompletableFuture<? extends T>... array) {
    return ofFutureList(CompletableFuture.allOf(array).thenApply(__ -> Stream.of(array)
            .map(CompletableFuture::join)
            .collect(Collectors.toList())));
  }

  public static <T> ListPromise<T> ofFutureList(CompletionStage<List<T>> future) {
    return future instanceof ListPromise<T> already ? already : new ListPromise<T>().completeWith(future);
  }

  public <O> ListPromise<O> thenMap(Function<? super T, O> mapper) {
    return asListPromise(() -> baseApply(value -> value.stream().map(mapper).toList()));
  }

  public <O> ListPromise<O> thenFlatMap(Function<? super T, ? extends Stream<O>> mapper) {
    return asListPromise(() -> baseApply(value -> value.stream().flatMap(mapper).toList()));
  }

  public <O> ListPromise<O> thenMapCompose(Function<? super T, ? extends CompletableFuture<O>> mapper) {
    return asListPromise(() -> baseCompose(value -> CompletableFutures.allAsList(value.stream().map(mapper))));
  }

  public <O> ListPromise<O> thenFlatMapCompose(Function<? super T, ? extends CompletableFuture<List<O>>> mapper) {
    return asListPromise(() -> baseCompose(value -> CompletableFutures.allAsList(value.stream().map(mapper))
            .thenApply(lists -> lists.stream().flatMap(List::stream).toList())));
  }

  public ListPromise<T> thenFilter(Predicate<? super T> filter) {
    return asListPromise(() -> baseApply(value -> value.stream().filter(filter).toList()));
  }

  public <V> ListPromise<V> thenFilter(Class<V> filterClass) {
    return asListPromise(() -> baseApply(value -> value.stream().filter(filterClass::isInstance).map(filterClass::cast).toList()));
  }

  public <O> Promise<O> thenFoldLeft(O identity, BiFunction<? super O, ? super T, ? extends O> folder) {
    return thenApply(list -> MoreStreams.foldLeft(identity, list.stream(), folder));
  }

  @Override
  protected PromiseFactory<ListPromise<T>> factory() {
    return ListPromise::new;
  }
}
