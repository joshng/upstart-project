package upstart.util.concurrent;

import upstart.util.collect.MoreStreams;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ListPromise<T> extends ExtendedPromise<List<T>, ListPromise<T>> {
  @SuppressWarnings("unchecked")
  public static <T> ListPromise<T> empty() {
    return (ListPromise<T>) EmptyPromise.INSTANCE;
  }

  public static <T> ListPromise<T> completed(@Nonnull List<T> list) {
    return list.isEmpty() ? empty() : new ListPromise<T>().fulfill(list);
  }

  @SafeVarargs
  public static <T> ListPromise<T> allAsList(CompletableFuture<? extends T>... array) {
    return array.length == 0
            ? empty()
            : Promise.of(CompletableFuture.allOf(array))
                    .thenApplyStream(ignored -> Stream.of(array).map(CompletableFuture::join));
  }

  public static <T> ListPromise<T> ofFutureList(CompletableFuture<List<T>> future) {
    return future instanceof ListPromise<T> already
            ? already
            : CompletableFutures.isCompletedNormally(future)
                    ? completed(future.join())
                    : new ListPromise<T>().completeWith(future);
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
  protected Promise.PromiseFactory<ListPromise<T>> factory() {
    return ListPromise::new;
  }

  private static class EmptyPromise extends ListPromise<Object> {
    static final EmptyPromise INSTANCE = new EmptyPromise();

    EmptyPromise() {
      complete(List.of());
    }

    @Override
    public <O> ListPromise<O> thenMap(Function<? super Object, O> mapper) {
      return empty();
    }

    @Override
    public <O> ListPromise<O> thenFlatMap(Function<? super Object, ? extends Stream<O>> mapper) {
      return empty();
    }

    @Override
    public <O> ListPromise<O> thenMapCompose(Function<? super Object, ? extends CompletableFuture<O>> mapper) {
      return empty();
    }

    @Override
    public <O> ListPromise<O> thenFlatMapCompose(Function<? super Object, ? extends CompletableFuture<List<O>>> mapper) {
       return empty();
    }

    @Override
    public ListPromise<Object> thenFilter(Predicate<? super Object> filter) {
       return empty();
    }

    @Override
    public <V> ListPromise<V> thenFilter(Class<V> filterClass) {
       return empty();
    }

    @Override
    public <O> Promise<O> thenFoldLeft(O identity, BiFunction<? super O, ? super Object, ? extends O> folder) {
       return Promise.completed(identity);
    }
  }
}
