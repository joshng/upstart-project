package upstart.util.strings;

import org.junit.jupiter.api.Test;

import static com.google.common.truth.Truth.assertThat;

class MoreStringsTest {
  @Test
  void lowerCamelToSnakeCase() {
    assertThat(MoreStrings.toUpperSnakeCase("lowerCamelCaseString")).isEqualTo("LOWER_CAMEL_CASE_STRING");
  }

  @Test
  void upperCamelToSnakeCase() {
    assertThat(MoreStrings.toUpperSnakeCase("UpperCamelCaseString")).isEqualTo("UPPER_CAMEL_CASE_STRING");
  }

  @Test
  void alreadySnakeCase() {
    assertThat(MoreStrings.toUpperSnakeCase("SNAKE_CASE_STRING")).isEqualTo("SNAKE_CASE_STRING");
  }

}