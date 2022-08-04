package upstart.config;

import com.google.common.util.concurrent.Service;
import com.google.inject.Module;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigValueFactory;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.LoggerFactory;
import upstart.UpstartService;
import upstart.log.UpstartLogConfig;
import upstart.test.UpstartExtension;
import upstart.util.concurrent.LazyReference;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.google.common.truth.Truth.assertWithMessage;
import static upstart.config.UpstartEnvironment.UPSTART_ENVIRONMENT;

/**
 * Provides a framework for confirming that the HOCON configurations for all known UPSTART_ENVIRONMENTs are
 * well-formed and pass validation. This is achieved by assembling (but <em>not</em> {@link Service#startAsync starting})
 * a {@link UpstartService} with the {@link Module}(s) associated with your application, using the configs
 * for each environment listed in upstart-upstart-env-registry.conf as prod or stage
 * <p/>
 * To set up configuration-validation for your application:
 * <ul>
 *   <li>Implement a subclass of EnvironmentConfigValidatorTest with access to your application's classpath</li>
 *   <li>In the {@link #configure} method, perform the guice configuration that matches your
 *   application's main-method (this should ideally just require installing one {@link Module}).
 *   </li>
 *   <li>If necessary, override the {@link #applyEnvironmentValues} method to provide placeholders for any
 *   values expected to be provided at runtime (via environment-variables or system-properties), using the
 *   provided {@link TestConfigBuilder} API, or delegate this responsibility to a reusable {@link EnvironmentConfigFixture}
 *   by annotating your test with {@link EnvironmentConfig.Fixture}</li>
 *   <li>list each target deployment-environment in a resource named upstart-env-registry.conf</li>
 * </ul>
 * For example:
 * <pre>{@code
 * class MyAppConfigValidator extends EnvironmentConfigValidator {
 *   @Override
 *   protected void configure() {
 *     install(MyAppModule.class);
 *   }
 *
 *   @Override
 *   protected void applyEnvironmentValues(EnvironmentConfig environmentConfig) {
 *     environmentConfig.setPlaceholder("my.app.context.value");
 *   }
 * }
 * }</pre>
 *
 * @see EnvironmentConfig.Fixture
 */
@ExtendWith(EnvironmentConfigExtension.class)
public abstract class EnvironmentConfigValidatorTest extends UpstartModule {
  static {
    UpstartExtension.ensureInitialized();
  }

  private static final LazyReference<List<UpstartEnvironment>> PROD_LIKE_ENVIRONMENTS = new LazyReference<>() {
    @Override
    protected List<UpstartEnvironment> supplyValue() {
      return UpstartEnvironmentRegistry.defaultClassLoaderRegistry().allRegisteredEnvironments().stream()
              .filter(env -> env.deploymentStage().isProductionLike())
              .sorted(Comparator.comparing(UpstartEnvironment::name))
              .collect(Collectors.toList());
    }
  };

  /**
   * Implement this method to {@link #install} all of the guice {@link Module}s that are used by your application
   * in production.
   */
  @Override
  protected abstract void configure();

  /**
   * Override this method to populate config values that are expected to be obtained via the runtime environment
   * (ie, shell environment-variables and/or system-properties populated with -D). The provided {@link EnvironmentConfigBuilder}
   * API offers methods for setting placeholder-values for these.
   * @param config
   */
  public void applyEnvironmentValues(EnvironmentConfigBuilder config) {
  }

  @TestFactory
  Stream<DynamicTest> validateEnvironmentConfigs(EnvironmentConfigBuilder configBuilder) {
    assertWithMessage("UPSTART_OVERRIDES environment-variable should be unset")
            .that(System.getenv(UpstartEnvironment.UPSTART_OVERRIDES)).isNull();
//    assertWithMessage("Production-like environments (specified in upstart-env-registry.conf)")
//            .that(PROD_LIKE_ENVIRONMENTS.get()).isNotEmpty();
    if (PROD_LIKE_ENVIRONMENTS.get().isEmpty()) {
      LoggerFactory.getLogger(getClass()).warn("No production-like environments defined in env-registry.conf");
      return Stream.empty();
    }

    configBuilder.setLogThreshold("upstart", UpstartLogConfig.LogThreshold.WARN);

    return PROD_LIKE_ENVIRONMENTS.get().stream()
            .map(env -> DynamicTest.dynamicTest(env.name(), () -> {
              try {
                EnvironmentConfigBuilder envConfigurator = configBuilder.copy().setEnvironmentName(env.name());

                applyEnvironmentValues(envConfigurator);

                Config environmentNameConfig = ConfigValueFactory.fromAnyRef(env.name(), getClass().getSimpleName())
                        .atPath(UPSTART_ENVIRONMENT);
                Config overrideConfig = environmentNameConfig.withFallback(envConfigurator.getOverrideConfig());

                HojackConfigProvider overriddenConfig = env.configProvider().withOverrideConfig(overrideConfig);

                // configs are deemed valid if we can build an application for this environment
                UpstartService.builder(overriddenConfig)
                        .installModule(this)
                        .build();
              } catch (Exception e) {
                throw new AssertionError("Config validation failed for for environment '" + env.name() + "':\n" + e.getMessage(), e);
              }
            }));
  }

}
