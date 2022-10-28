package upstart.aws;

import reactor.core.publisher.Flux;
import upstart.util.concurrent.ListPromise;

public interface FluxPromise {
  /**
   * this can be used with {@link Flux::as} to convert a {@link Flux} to a {@link ListPromise}
   */
  static <T> ListPromise<T> toListPromise(Flux<T> flux) {
    return ListPromise.ofFutureList(flux.collectList().toFuture());
  }
}
