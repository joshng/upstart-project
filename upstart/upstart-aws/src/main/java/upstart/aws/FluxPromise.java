package upstart.aws;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import upstart.util.concurrent.ListPromise;
import upstart.util.concurrent.OptionalPromise;
import upstart.util.concurrent.Promise;

public interface FluxPromise {
  /**
   * this can be used with {@link Flux::as} to convert a {@link Flux} to a {@link ListPromise}
   */
  static <T> ListPromise<T> list(Flux<T> flux) {
    return ListPromise.ofFutureList(flux.collectList().toFuture());
  }

  static <T> Promise<T> promise(Mono<T> mono) {
    return Promise.of(mono.toFuture());
  }

  static <T> OptionalPromise<T> optional(Mono<T> mono) {
    return OptionalPromise.ofFutureNullable(mono.toFuture());
  }
}
