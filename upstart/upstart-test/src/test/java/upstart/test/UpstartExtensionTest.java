package upstart.test;

import com.google.inject.name.Names;
import upstart.config.annotations.ConfigPath;
import upstart.config.UpstartModule;
import org.immutables.value.Value;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;
import javax.inject.Named;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;


@UpstartTest
class UpstartExtensionTest extends UpstartModule {
  @Override
  protected void configure() {
    bindConfig(BogusConfig.class);
  }

  @BeforeEach
  void setup(UpstartTestBuilder config) {
    config
            .overrideConfig("bogus.test.config", BogusConfig.of("surprise!", 7))
            .overrideConfig("bogus.test.config.i", 3)
    ;
  }

  @Inject
  BogusConfig config;
  @Test
  void checkEverything(UpstartTestBuilder configurator) {
    assertThat(config.i()).isEqualTo(3);
    assertThat(config.string()).isEqualTo("surprise!");
    assertThrows(IllegalStateException.class, () -> configurator.overrideConfig("abc", 123));
  }

  @ConfigPath("bogus.test.config")
  interface BogusConfig {
    static BogusConfig of(String setting, int i) {
      return ImmutableBogusConfig.of(setting, i);
    }
    @Value.Parameter
    String string();

    @Value.Parameter
    int i();
  }

  @Nested
  class NestedTest extends UpstartModule {

    @BeforeEach
    void setup(UpstartTestBuilder config) {
      config.overrideConfig("bogus.test.config.i", 9);
    }

    @Inject @Named("nested-value") String nestedValue;

    @Override
    public void configure() {
      bindConstant().annotatedWith(Names.named("nested-value")).to("test-value");
    }

    @Test
    void nestedAndOuterValuesAreHandled() {
      assertThat(config.string()).isEqualTo("surprise!");
      assertThat(config.i()).isEqualTo(9);
      assertThat(nestedValue).isEqualTo("test-value");
    }
  }
}