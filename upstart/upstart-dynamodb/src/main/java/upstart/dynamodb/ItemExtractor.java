package upstart.dynamodb;

import upstart.util.concurrent.Promise;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

@FunctionalInterface
public interface ItemExtractor<B, T> {

  @SuppressWarnings("rawtypes") ItemExtractor IDENTITY = new ItemExtractor<>() {
    @Override
    public CompletableFuture<Object> extract(Object blob) {
      return Promise.completed(blob);
    }

    @Override
    public <V> ItemExtractor<Object, V> andThen(ItemTransformer<? super Object, ? super Object, V> after) {
      return blob -> after.apply(blob, blob);
    }
  };

  CompletableFuture<T> extract(B blob);

  default <V> ItemExtractor<B, V> andThen(ItemTransformer<? super B, ? super T, V> after) {
    return blob -> extract(blob).thenCompose(result -> after.apply(blob, result));
  }

  @SuppressWarnings("unchecked")
  static <B> ItemExtractor<B, B> identity() {
    return IDENTITY;
  }

  static <B, T> ItemExtractor<B, T> of(Function<? super B, T> transformer) {
    return blob -> Promise.completed(transformer.apply(blob));
  }
}
