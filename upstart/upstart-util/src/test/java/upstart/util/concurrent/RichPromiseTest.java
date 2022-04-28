package upstart.util.concurrent;

import org.junit.jupiter.api.Test;
import upstart.util.collect.Optionals;

import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static com.google.common.truth.Truth.assertThat;

class RichPromiseTest {
  @Test
  void exerciseMonadicPromiseSpecializations() {
    assertThat(
            Promise.completed(3)
                    .thenApplyOptional(Optional::of)
                    .thenComposeList(n -> CompletableFuture.completedFuture(Collections.nCopies(n.get(), "abc")))
                    .thenFoldLeft(0, (n, str) -> n + str.length())
                    .thenApplyOptional(n -> Optionals.onlyIf(n > 2, n))
                    .thenFilter(n -> n > 3)
                    .orElseThrow()
                    .join()
    ).isEqualTo(9);
  }

}