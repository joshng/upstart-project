package upstart.util;

import org.junit.jupiter.api.Test;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;

class BooleanChoiceTest {
  @Test
  void choosesFirstTrueValue() {
    assertThat(
            BooleanChoice.of(false, 1)
                    .or(false, 2)
                    .or(true, 3)
                    .or(false, 4)
                    .otherwise(99)
    ).isEqualTo(3);
  }

  @Test
  void returnsOtherwiseIfUnmatched() {
    assertThat(
            BooleanChoice.of(false, 1)
                    .or(false, 2)
                    .or(false, 3)
                    .or(false, 4)
                    .otherwise(99)
    ).isEqualTo(99);
  }

  @Test
  void returnsPresentOptionalWithResult() {
    assertThat(
            BooleanChoice.of(false, 1)
                    .or(false, 2)
                    .or(true, 3)
                    .or(false, 4)
                    .result()
    ).hasValue(3);
  }

  @Test
  void returnsAbsentOptionalIfUnmatched() {
    assertThat(
            BooleanChoice.of(false, 1)
                    .or(false, 2)
                    .result()
    ).isEmpty();
  }

}