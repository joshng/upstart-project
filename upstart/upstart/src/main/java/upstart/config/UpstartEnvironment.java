package upstart.config;

import com.google.common.collect.ImmutableMap;
import upstart.services.UpstartService;
import upstart.UpstartDeploymentStage;
import upstart.util.Ambiance;
import upstart.util.LocalHost;
import upstart.util.collect.MoreStreams;
import upstart.util.collect.Optionals;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigParseOptions;
import org.immutables.value.Value;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Optional;

/**
 * Holds the configuration for a {@link UpstartService}, as selected by the ambient value of
 * UPSTART_ENVIRONMENT (environment-variable or system-property).
 * <p/>
 * By default, configs are resolved with the following precedence (ordered highest to lowest):
 * <ol>
 *   <li>contents of UPSTART_OVERRIDES</li>
 *   <li>System environment-variables</li>
 *   <li>dev-configs/${USERNAME}.conf (only if UpstartDeploymentStage=dev)</li>
 *   <li>upstart-environments/${UPSTART_ENVIRONMENT}.conf</li>
 *   <li>System properties</li>
 *   <li>upstart-application.conf</li>
 *   <li>application.conf</li>
 *   <li>upstart-defaults.conf</li>
 *   <li>reference.conf</li>
 *   <li>upstart-defaults/${ConfigPath}.conf for each binding loaded with {@link UpstartModule#bindConfig}</li>
 * </ol>
 */
@Value.Immutable
public abstract class UpstartEnvironment {
  public static final String UPSTART_ENVIRONMENT = "UPSTART_ENVIRONMENT";
  public static final String UPSTART_OVERRIDES = "UPSTART_OVERRIDES";
  private static final Config HOSTNAME_CONFIG = ConfigFactory.parseMap(ImmutableMap.of(
          "upstart.localhost.hostname", LocalHost.getLocalHostname(),
          "upstart.localhost.ipAddress", LocalHost.getLocalIpAddress()
  ));

  /**
   *
   * @return
   */
  public static UpstartEnvironment ambientEnvironment() {
    return AmbientConfigHolder.DEFAULT_ENVIRONMENT;
  }

  public static UpstartEnvironment forAmbientEnvironment(ClassLoader classLoader) {
    return of(Ambiance.requiredAmbientValue(UPSTART_ENVIRONMENT), classLoader);
  }

  public static UpstartEnvironment of(String name, ClassLoader classLoader) {
    return UpstartEnvironmentRegistry.get(classLoader).environment(name);
  }

  public static ImmutableUpstartEnvironment.Builder builder() {
    return ImmutableUpstartEnvironment.builder();
  }

  public static HojackConfigProvider defaultConfigProvider() {
    return ambientEnvironment().configProvider();
  }

  public static <T> T loadAmbientConfigValue(Class<T> singletonConfigType) {
    return loadAmbientConfigValue(ConfigKey.of(singletonConfigType));
  }

  public static <T> T loadAmbientConfigValue(ConfigKey<T> of) {
    return defaultConfigProvider().getConfigObject(of).mappedObject();
  }

  public abstract String name();

  public abstract ClassLoader classLoader();

  public abstract UpstartDeploymentStage deploymentStage();

  public UpstartService.Builder buildApplication() {
    return UpstartService.builder(this);
  }

  @Value.Lazy
  public HojackConfigProvider configProvider() {
    return HojackConfigProvider.builder().baseConfig(baseConfig()).deploymentStage(deploymentStage()).build();
  }

  @Value.Lazy
  public Config baseConfig() {
    Config devConfig = devConfig().orElse(ConfigFactory.empty());

    Config upstartOverrideConfig = upstartOverrideConfig().orElse(ConfigFactory.empty());

    Config overrideConfig = upstartOverrideConfig
            .withFallback(ConfigFactory.systemEnvironment())
            .withFallback(devConfig)
            .withFallback(ConfigFactory.parseMap(Map.of("upstart.deploymentStage", deploymentStage().toString())));

    ConfigParseOptions classLoaderOptions = ConfigParseOptions.defaults().setClassLoader(classLoader());

    Config environmentConfig = ConfigFactory.parseResourcesAnySyntax("upstart-environments/" + name(), classLoaderOptions.setAllowMissing(false));

    return HOSTNAME_CONFIG
            .withFallback(contextConfig())
            .withFallback(overrideConfig)
            .withFallback(environmentConfig)
            .withFallback(ConfigFactory.systemProperties())
            .withFallback(ConfigFactory.parseResourcesAnySyntax("upstart-application", classLoaderOptions))
            .withFallback(ConfigFactory.defaultApplication(classLoaderOptions))
            .withFallback(ConfigFactory.parseResourcesAnySyntax("upstart-defaults", classLoaderOptions))
            .withFallback(ConfigFactory.defaultReference(classLoaderOptions.getClassLoader()));
  }

  @Value.Derived
  @Value.Auxiliary
  public Config contextConfig() {
    return ConfigFactory.parseMap(
            ImmutableMap.of("environment", name(), "deploymentStage", deploymentStage().name()),
            "UPSTART_ENVIRONMENT"
    ).atPath("upstart.context");
  }

  @Value.Derived
  @Value.Auxiliary
  public Optional<Config> upstartOverrideConfig() {
    return Ambiance.ambientValue(UPSTART_OVERRIDES)
            .map(s -> ConfigFactory.parseString(s, ConfigParseOptions.defaults()
                    .setOriginDescription(UPSTART_OVERRIDES)));
  }

  @Value.Derived
  @Value.Auxiliary
  public Optional<Config> devConfig() {
    return Optionals.onlyIfFrom(
            deploymentStage().isDevelopmentMode(),
            UpstartEnvironment::loadDevConfig
    );
  }

  private static Config loadDevConfig() {
    return Optionals.or(
            Ambiance.ambientValue("UPSTART_DEV_CONFIG").map(File::new),
            UpstartEnvironment::findDevConfigFile
    ).map(ConfigFactory::parseFileAnySyntax)
            .orElse(null);
  }

  private static Optional<File> findDevConfigFile() {
    String username = System.getProperty("user.name");
    Path configDir = Paths.get("dev-configs");
    Path cwd = Paths.get(".").toAbsolutePath();
    try {
      return MoreStreams.generate(cwd, Path::getParent)
              .map(path -> path.resolve(configDir))
              .filter(path -> path.toFile().isDirectory())
              .map(devDir -> devDir.resolve(username).toFile())
              .findFirst();
    } catch (Exception e) {
      return Optional.empty();
    }
  }

  @Override
  public String toString() {
    return name();
  }

  private static class AmbientConfigHolder {
    static UpstartEnvironment DEFAULT_ENVIRONMENT = forAmbientEnvironment(ClassLoader.getSystemClassLoader());
  }
}
