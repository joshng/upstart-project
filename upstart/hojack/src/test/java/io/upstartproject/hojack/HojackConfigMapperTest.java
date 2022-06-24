package io.upstartproject.hojack;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.google.common.truth.Truth.assertThat;

class HojackConfigMapperTest {
  @Test
  void basicNumberedList() {
    HojackConfigMapper mapper = new HojackConfigMapper();
    Config config = ConfigFactory.parseString("example.strings.0: foo, example.strings: ${?example.strings} [bar, baz]").resolve();
    TestConfig mapped = mapper.mapSubConfig(config, "example", TestConfig.class);
    assertThat(mapped.strings).isEqualTo(List.of("foo", "bar", "baz"));
  }

  static class TestConfig {
    public List<String> strings;
  }
}