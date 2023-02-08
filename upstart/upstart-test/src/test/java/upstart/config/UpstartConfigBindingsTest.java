package upstart.config;

import com.google.inject.Injector;
import io.upstartproject.hojack.Size;
import upstart.UpstartService;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigMemorySize;
import org.junit.jupiter.api.Test;
import upstart.config.annotations.ConfigPath;
import upstart.test.UpstartContextFixture;
import upstart.test.UpstartTest;

import java.io.StringReader;
import java.time.Duration;

import static com.google.common.truth.Truth.assertThat;


@UpstartContextFixture
class UpstartConfigBindingsTest {
  @Test
  void configWiringWorks() {
    String hocon = "upstart { context {application: fake, owner: test}, test.fake {connectionString: testCoords, duration: 10s, size: 1k, size2: 1GB}}";
    System.setProperty("UPSTART_ENVIRONMENT", UpstartTest.TEST_ENVIRONMENT_NAME);
    UpstartConfigProvider environment = UpstartEnvironment.ambientEnvironment().configProvider()
            .withOverrideConfig(ConfigFactory.parseReader(new StringReader(hocon)));

    Injector injector = UpstartService.builder(environment)
            .installModule(new UpstartModule() {
              @Override
              protected void configure() {
                bindConfig(FakeConfig.class);
              }
            }).buildInjector();

    FakeConfig parsed = injector.getInstance(FakeConfig.class);

    assertThat(parsed.connectionString()).isEqualTo("testCoords");

    assertThat(parsed.duration()).isEqualTo(Duration.ofSeconds(10));
    assertThat(parsed.size2().toBytes()).isEqualTo(1073741824);
  }

  @ConfigPath(value = "upstart.test.fake")
  public interface FakeConfig {
    String connectionString();
    Size size();
    ConfigMemorySize size2();
    Duration duration();
    int defaultNumber();
  }
}