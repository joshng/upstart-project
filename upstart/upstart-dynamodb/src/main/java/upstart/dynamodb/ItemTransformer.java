package upstart.dynamodb;

import upstart.util.concurrent.Promise;

import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Function;

public interface ItemTransformer<B, I, T> extends BiFunction<B, I, CompletableFuture<T>> {
  ItemTransformer<?, ?, ?> IDENTITY = new ItemTransformer<>() {
    @Override
    public CompletableFuture<Object> apply(Object bean, Object intermediate) {
      return CompletableFuture.completedFuture(intermediate);
    }

    @Override
    public ItemExtractor<Object, Object> compose(ItemExtractor<Object, Object> extractor) {
      return extractor;
    }

    @Override
    public <V> ItemTransformer<Object, Object, V> andThen(ItemTransformer<? super Object, ? super Object, V> after) {
      return after;
    }

    @Override
    public <V> BiFunction<Object, Object, V> andThen(Function<? super CompletableFuture<Object>, ? extends V> after) {
      return (bean, intermediate) -> after.apply(CompletableFuture.completedFuture(intermediate));
    }
  };

  @SuppressWarnings("unchecked")
  static <B, T> ItemTransformer<B, T, T> identity() {
    return (ItemTransformer<B, T, T>) IDENTITY;
  }
  
  static <I, T> ItemTransformer<Object, I, T> of(Function<? super I, T> transformer) {
    return (bean, intermediate) -> Promise.completed(transformer.apply(intermediate));
  }

  CompletableFuture<T> apply(B bean, I intermediate);

  default <V> ItemTransformer<B, I, V> andThen(ItemTransformer<? super B, ? super T, V> after) {
    return (B bean, I intermediate) -> apply(bean, intermediate).thenCompose(result -> after.apply(bean, result));
  }

  default <V> ItemTransformer<B, I, V> andThenApply(BiFunction<? super B, ? super T, V> after) {
    return (B bean, I intermediate) -> apply(bean, intermediate).thenApply(result -> after.apply(bean, result));
  }
  
  
  default <V> ItemTransformer<B, I, V> andThenApply(Function<? super T, V> after) {
    return andThen(of(after));
  }

  default ItemExtractor<B, T> compose(ItemExtractor<B, I> extractor) {
    return extractor.andThen(this);
  }
}
