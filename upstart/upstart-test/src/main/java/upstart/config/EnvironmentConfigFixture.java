package upstart.config;

import org.junit.jupiter.api.extension.ExtensionContext;

import java.util.Optional;

public interface EnvironmentConfigFixture {
  void applyEnvironmentValues(TestConfigBuilder<?> config, Optional<ExtensionContext> testExtensionContext);
}
