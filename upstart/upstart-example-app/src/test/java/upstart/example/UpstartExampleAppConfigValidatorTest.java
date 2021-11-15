package upstart.example;

import upstart.config.EnvironmentConfigBuilder;
import upstart.config.EnvironmentConfigValidatorTest;

public class UpstartExampleAppConfigValidatorTest extends EnvironmentConfigValidatorTest {
  @Override
  protected void configure() {
    install(CountReportingService.ReportingModule.class);
  }

  @Override
  public void applyEnvironmentValues(EnvironmentConfigBuilder config) {
    // inject any expected ambient configuration into the config here
    // (eg, values that would be delivered via environment-variables or system-properties)
  }
}
