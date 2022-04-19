package upstart.config;

import upstart.config.annotations.ConfigPath;
import upstart.test.UpstartContextFixture;
import upstart.test.UpstartTest;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static com.google.common.truth.Truth.assertThat;

@UpstartContextFixture
@UpstartTest
public class EnvironmentConfigValidatorTestTest extends EnvironmentConfigValidatorTest {
  private ExampleConfig exampleConfig;

  @Override
  protected void configure() {
    exampleConfig = bindConfig(ExampleConfig.class);
  }

  @Test
  void fixtureDoesNotRunOnOuterTest() {
    assertThat(exampleConfig.intValue()).isEqualTo(-1); // from upstart-defaults/example.config.conf
  }

  @EnvironmentConfig.Fixture(impl = ExampleConfigFixture.class, resources = "exampleConfigFixture.conf")
  @Nested
  class WithNestedConfig {
    @Test
    void fixtureRunsOnAnnotatedNestedTest() {
      assertThat(exampleConfig.intValue()).isEqualTo(1); // from ExampleConfigFixture
    }

    @EnvironmentConfig.Fixture(impl = ExampleConfigFixture.class)
    @Nested
    class WithRedundantNestedConfig {
      @Test
      void redundantFixtureIsDeduped() {
        assertThat(exampleConfig.intValue()).isEqualTo(2); // from ExampleConfigFixture
      }

      @EnvironmentConfig.Fixture(resources = "exampleConfigFixture.conf")
      @Nested
      class WithOverridingResourceConfig {
        @Test
        void innerResourceConfigTakesPrecedence() {
          assertThat(exampleConfig.intValue()).isEqualTo(99); // from exampleConfigFixture.conf
        }
      }
    }
  }

  @ConfigPath("example.config")
  interface ExampleConfig {
    int intValue();
  }

  public static class ExampleConfigFixture implements EnvironmentConfigFixture {
    static final AtomicInteger INVOCATION_COUNTER = new AtomicInteger();

    @Override
    public void applyEnvironmentValues(TestConfigBuilder<?> config) {
      config.overrideConfig("example.config.intValue", INVOCATION_COUNTER.incrementAndGet());
    }
  }
}
