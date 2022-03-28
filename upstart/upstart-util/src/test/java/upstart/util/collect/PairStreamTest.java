package upstart.util.collect;

import com.google.common.collect.ImmutableMap;
import org.junit.jupiter.api.Test;

import java.util.stream.Stream;

import static com.google.common.truth.Truth8.assertThat;

class PairStreamTest {
  @Test
  void swap() {
    assertThat(subject().swap()).containsExactly(Pair.of(1, "k1"), Pair.of(2, "k2")).inOrder();
  }

  @Test
  void mapKeys() {
    assertThat(subject().mapKeys(k -> k + "mapped")).containsExactly(Pair.of("k1mapped", 1), Pair.of("k2mapped", 2)).inOrder();
  }

  @Test
  void mapValues() {
    assertThat(subject().mapValues(v -> v + "mapped")).containsExactly(Pair.of("k1", "1mapped"), Pair.of("k2", "2mapped")).inOrder();
  }

  @Test
  void flatMapKeys() {
    assertThat(subject().flatMapKeys(k -> Stream.of(k, k + "+")))
            .containsExactly(
                    Pair.of("k1", 1),
                    Pair.of("k1+", 1),
                    Pair.of("k2", 2),
                    Pair.of("k2+", 2)
            ).inOrder();
    assertThat(subject().flatMapKeys((k, v) -> Stream.of(k, k + "=" + v)))
            .containsExactly(
                    Pair.of("k1", 1),
                    Pair.of("k1=1", 1),
                    Pair.of("k2", 2),
                    Pair.of("k2=2", 2)
            ).inOrder();
  }

  @Test
  void zip() {
    assertThat(PairStream.zip(naturalNumbers(0, 2), naturalNumbers(5, 5)))
            .containsExactly(Pair.of(0, 5), Pair.of(1, 6)).inOrder();

  }

  @Test
  void zipWithIndex() {
    assertThat(PairStream.zipWithIndex(naturalNumbers(2, 3)))
            .containsExactly(Pair.of(2, 0L), Pair.of(3, 1L), Pair.of(4, 2L)).inOrder();
  }

  @Test
  void cartesianProduct() {
    assertThat(PairStream.cartesianProduct(naturalNumbers(0, 2), () -> naturalNumbers(5, 2)))
            .containsExactly(Pair.of(0, 5), Pair.of(0, 6), Pair.of(1, 5), Pair.of(1, 6)).inOrder();
  }

  @Test
  void keys() {
    assertThat(subject().keys()).containsExactly("k1", "k2").inOrder();
  }

  @Test
  void values() {
    assertThat(subject().values()).containsExactly(1, 2).inOrder();
  }

  private static Stream<Integer> naturalNumbers(int first, int size) {
    return MoreStreams.generate(first, x -> x + 1).limit(size);
  }

  private static PairStream<String, Integer> subject() {
    return PairStream.of(ImmutableMap.of("k1", 1, "k2", 2));
  }
}