package upstart.util;

import com.google.common.truth.Truth;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import static com.google.common.truth.Truth8.assertThat;

class MoreStreamsTest {
  @Test
  void append() {
    assertThat(MoreStreams.append(naturalNumbers().limit(2), 99)).containsExactly(1, 2, 99).inOrder();
  }

  @Test
  void foldLeft() {
    Truth.assertThat(MoreStreams.foldLeft(0, naturalNumbers().limit(4), Integer::sum)).isEqualTo(10);
  }

  @Test
  void scan() {
    assertThat(MoreStreams.scan(0, naturalNumbers().limit(4), Integer::sum)).containsExactly(1, 3, 6, 10).inOrder();
  }

  @Test
  void span() {
    Pair<Stream<Integer>, CompletableFuture<Stream<Integer>>> beforeAndAfter = MoreStreams.span(naturalNumbers().limit(6), x -> x < 3);
    assertThat(beforeAndAfter.getKey()).containsExactly(1, 2).inOrder();
    assertThat(beforeAndAfter.getValue().join()).containsExactly(3, 4, 5, 6).inOrder();
  }

  private static Stream<Integer> naturalNumbers() {
    return MoreStreams.generate(1, x -> x + 1);
  }

}