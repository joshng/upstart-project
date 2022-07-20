package upstart.util.concurrent;

import org.junit.jupiter.api.Test;
import upstart.util.collect.Optionals;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static com.google.common.truth.Truth.assertThat;

class ExtendedPromiseTest {
  @Test
  void exerciseMonadicPromiseSpecializations() {
    assertThat(
            Promise.completed(3)
                    .thenApplyOptional(Optional::of)
                    .thenComposeList(n -> CompletableFuture.completedFuture(Collections.nCopies(n.orElseThrow(), "abc")))
                    .thenFoldLeft(0, (n, str) -> n + str.length())
                    .thenApplyOptional(n -> Optionals.onlyIf(n > 2, n))
                    .thenFilter(n -> n > 3)
                    .orElseThrow()
                    .join()
    ).isEqualTo(9);
  }

  @Test
  void recoverReturnsSameType() {
    assertThat(OptionalPromise.of(1).recover(Exception.class, e -> Optional.of(2))).isInstanceOf(OptionalPromise.class);
    assertThat(ListPromise.asListPromise(1).recover(Exception.class, e -> List.of(2))).isInstanceOf(ListPromise.class);
  }

  @Test
  void whenCompleteReturnsSameType() {
    assertThat(new OptionalPromise<Integer>().whenComplete((v, e) -> {})).isInstanceOf(OptionalPromise.class);
  }

  @Test
  void exceptionallyReturnsSameType() {
    assertThat(new OptionalPromise<Integer>().exceptionally(e -> Optional.of(2))).isInstanceOf(OptionalPromise.class);
  }

  @Test
  void thenMap() {
    OptionalPromise<Integer> p = new OptionalPromise<>();
    OptionalPromise<Integer> chained = p.thenMap(v -> v * 3);
    assertThat(chained).isInstanceOf(OptionalPromise.class);
    assertThat(p.fulfill(Optional.of(1)).join()).isEqualTo(Optional.of(1));
    assertThat(chained.join()).isEqualTo(Optional.of(3));
  }
}