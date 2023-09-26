package upstart.dynamodb;

import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;

public interface ItemTransformer<B, I, T> extends BiFunction<B, I, CompletableFuture<T>> {
  CompletableFuture<T> apply(B blob, I intermediate);

  default <V> ItemTransformer<B, I, V> andThen(ItemTransformer<? super B, ? super T, V> after) {
    return (B blob, I intermediate) -> apply(blob, intermediate).thenCompose(result -> after.apply(blob, result));
  }
}
