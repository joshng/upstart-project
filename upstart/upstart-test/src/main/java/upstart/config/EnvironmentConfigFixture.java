package upstart.config;

public interface EnvironmentConfigFixture {
  void applyEnvironmentValues(TestConfigBuilder<?> config);
}
