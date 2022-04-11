package upstart.config;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableList;
import upstart.UpstartService;
import upstart.UpstartDeploymentStage;
import upstart.util.Ambiance;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.immutables.value.Value;

import java.util.List;
import java.util.Map;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * Sponsors the list of supported {@link UpstartEnvironment} configurations for a {@link UpstartService}.
 * <p/>
 * Loads environment declarations from an embedded resource named <strong>upstart-env-registry.conf</strong>, which
 * configures the {@link UpstartDeploymentStage} for each environment.
 * <p/>
 * Also used by {@link EnvironmentConfigValidatorTest}-based tests to discover the configurations to be validated.
*/
@Value.Immutable(builder = false)
@Value.Style(visibility = Value.Style.ImplementationVisibility.PACKAGE, allParameters = true)
public abstract class UpstartEnvironmentRegistry {
  private static final LoadingCache<ClassLoader, UpstartEnvironmentRegistry> BY_CLASSLOADER = CacheBuilder.newBuilder()
          .build(CacheLoader.from(ImmutableUpstartEnvironmentRegistry::of));
  private static final String DEFAULT_REGISTRY_RESOURCE_NAME = "upstart-env-registry.conf";
  public static final String UPSTART_ENV_REGISTRY_VARIABLE_NAME = "UPSTART_ENV_REGISTRY";
  public static final String REGISTRY_RESOURCE_NAME = Ambiance.ambientValue(UPSTART_ENV_REGISTRY_VARIABLE_NAME)
          .orElse(DEFAULT_REGISTRY_RESOURCE_NAME);

  private final LoadingCache<String, UpstartEnvironment> environmentCache = CacheBuilder.newBuilder()
          .build(new CacheLoader<String, UpstartEnvironment>() {
            @Override
            public UpstartEnvironment load(String name) {
              checkArgument(appEnvConfig().hasPath(name), "Unrecognized upstart environment name: '%s'. (Environments must be registered in %s)", name, REGISTRY_RESOURCE_NAME);
              UpstartDeploymentStage stage = appEnvConfig().getEnum(UpstartDeploymentStage.class, name);
              return ImmutableUpstartEnvironment.builder()
                      .name(name)
                      .classLoader(classLoader())
                      .deploymentStage(stage)
                      .build();
            }
          });

  public static UpstartEnvironmentRegistry defaultClassLoaderRegistry() {
    return get(ClassLoader.getSystemClassLoader());
  }

  public static UpstartEnvironmentRegistry get(ClassLoader classLoader) {
    return BY_CLASSLOADER.getUnchecked(classLoader);
  }

  @SuppressWarnings("unused")
  public abstract ClassLoader classLoader();

  @Value.Derived
  @Value.Auxiliary
  public List<UpstartEnvironment> allEnvironments() {
    return appEnvConfig().entrySet().stream()
            .map(Map.Entry::getKey)
            .map(this::environment)
            .collect(ImmutableList.toImmutableList());
  }

  UpstartEnvironment environment(String name) {
    return environmentCache.getUnchecked(name);
  }

  @Value.Derived
  @Value.Auxiliary
  Config appEnvConfig() {
    return ConfigFactory.parseResources(REGISTRY_RESOURCE_NAME)
            .getConfig("upstart.application.environments");
  }
}
