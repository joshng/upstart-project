package upstart;

import com.google.common.base.Ticker;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.Service;
import com.google.inject.AbstractModule;
import com.google.inject.CreationException;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.Scopes;
import com.google.inject.Stage;
import com.google.inject.spi.Message;
import io.upstartproject.hojack.ConfigMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import upstart.config.ConfigMappingException;
import upstart.config.HojackConfigProvider;
import upstart.config.UpstartApplicationConfig;
import upstart.config.UpstartConfigBinder;
import upstart.config.UpstartConfigProvider;
import upstart.config.UpstartContext;
import upstart.config.UpstartEnvironment;
import upstart.config.UpstartModule;
import upstart.config.annotations.ConfigPath;
import upstart.log.UpstartLogConfig;
import upstart.log.UpstartLogProvider;
import upstart.util.concurrent.services.BaseComposableService;
import upstart.managedservices.ManagedServiceGraph;
import upstart.managedservices.ManagedServicesModule;
import upstart.managedservices.ManagedServicesModule.ServiceManager;
import upstart.managedservices.ServiceLifecycle;
import upstart.util.concurrent.services.ServiceSupervisor;
import upstart.util.LocalHost;
import upstart.util.collect.Optionals;
import upstart.util.collect.PairStream;
import upstart.util.exceptions.Exceptions;
import upstart.util.reflect.Reflect;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * A managed upstart service!
 * <p>
 * <p/>
 * UpstartApplications configure their components by defining {@link UpstartModule} classes, which
 * use Guice {@link AbstractModule} methods as well as upstart-specific features such as
 * configuration-wiring via {@link UpstartModule#bindConfig}, and {@link Service}-lifecycle automation
 * via the {@link UpstartModule#serviceManager()}.
 * <p/>
 * Application <em>main</em>-methods will typically get started with a {@link #builder},
 * or just {@link #supervise}.
 *
 * @see UpstartApplication
 */
/*
 *  +------------------------------------------------------+
 *  |   ___ _    _ ___              _                _     |
 *  |  |  _| |  | |_  |            | |              | |    |
 *  |  | | | |  | | | | _ __   ___ | |_  __ _  _ __ | |_   |
 *  |  | | | |  | | | || '_ \ / __|| __|/ _` || '__|| __|  |
 *  |  | | | |__| | | || |_) |\__ \| |_| (_| || |   | |_   |
 *  |  | |_ \____/ _| || .__/ |___/ \__|\__,_||_|    \__|  |
 *  |  |___|      |___||_|                                 |
 *  |                                                      |
 *  +------------------------------------------------------+
 */
@Singleton
public final class UpstartService extends BaseComposableService<ManagedServiceGraph> {
  private static final Logger LOG = LoggerFactory.getLogger(UpstartService.class);
  private static final String USAGE = "Usage: java %s <fully.qualified.UpstartApplicationClass>".formatted(UpstartService.class.getName());
  private static volatile Injector s_latestInjector;

  static {
    UpstartStaticInitializer.ensureInitialized();
  }

  private final UpstartApplicationConfig applicationConfig;
  private final Injector injector;

  @Inject
  UpstartService(
          @ServiceLifecycle(ServiceLifecycle.Phase.Infrastructure) ManagedServiceGraph infrastructureGraph,
          UpstartApplicationConfig applicationConfig,
          Injector injector
  ) {
    super(infrastructureGraph);
    this.applicationConfig = applicationConfig;
    this.injector = injector;
    LOG.info("Services created:\n{}\n", delegate());
    ManagedServiceGraph applicationGraph = infrastructureGraph.getService(ManagedServiceGraph.class);
    Sets.SetView<Service> doubleRegisteredServices = Sets.intersection(
            applicationGraph.getAllServices(),
            infrastructureGraph.getAllServices()
    );
    checkArgument(doubleRegisteredServices.isEmpty(), "Services registered in multiple ServiceLifecycle phases: %s", doubleRegisteredServices);
  }

  public UpstartApplicationConfig config() {
    return applicationConfig;
  }

  /**
   * Starts a {@link Builder} with the default {@link UpstartEnvironment} (determined by UPSTART_ENVIRONMENT) and
   * the default {@link ConfigMapper}.
   *
   * @see UpstartEnvironment#ambientEnvironment()
   * @see #builder(UpstartEnvironment)
   */
  public static Builder builder() {
    return builder(UpstartEnvironment.ambientEnvironment().configProvider());
  }

  /**
   * Starts a {@link Builder} with the given {@link UpstartEnvironment} and the default {@link ConfigMapper}.
   */
  public static Builder builder(UpstartEnvironment env) {
    return builder(env.configProvider());
  }

  /**
   * Starts a {@link Builder} with the given {@link UpstartConfigProvider}
   */
  public static Builder builder(UpstartConfigProvider config) {
    return new Builder(config).installModule(new Builder.UpstartCoreModule());
  }

  public static Injector latestInjector() {
    return s_latestInjector;
  }

  public <T> T getInstance(Class<T> type) {
    return injector.getInstance(type);
  }

  public Injector injector() {
    return injector;
  }

  public static void supervise(UpstartApplication application) {
    application.buildServiceSupervisor().startAndAwaitTermination();
  }

  private static ServiceSupervisor.BuildFinal<UpstartService> buildSupervisor(UpstartApplication application) {
    return application.configureSupervisor(application.builder().buildServiceSupervisor());
  }

  public static void main(String[] args) {
    if (args.length != 1) {
      throw new IllegalArgumentException(USAGE + "\n\nInvalid args: '" + String.join("' '", args) + "'\n(To package an UpstartApplication, define <upstart.main.class> in pom.xml, and use upstart-app-parent");
    }
    UpstartApplication application;
    try {
      application = Class.forName(args[0]).asSubclass(UpstartApplication.class).getConstructor().newInstance();
    } catch (Exception e) {
      LOG.error(USAGE, e);
      throw new IllegalArgumentException(USAGE, e);
    }
    supervise(application);
  }

  @Override
  public String toString() {
    return delegate().toString();
  }

  /**
   * A convenience class for assembling the {@link Module Modules} to be used to build a {@link UpstartService},
   * and (optionally) a {@link #buildServiceSupervisor ServiceSupervisor} for it.
   */
  public static class Builder implements UpstartApplicationBuilder<Builder> {
    private final List<Module> modules = new ArrayList<>();
    private final UpstartConfigProvider configProvider;

    private Builder(UpstartConfigProvider configProvider) {
      this.configProvider = configProvider;
    }

    @Override
    public Builder installModule(Module module) {
      modules.add(module);
      return this;
    }

    /**
     * Begins configuring a {@link ServiceSupervisor} for the provided {@link #installModule Modules}:
     * this will prepare a fully-managed application with service-lifecycles matched to the lifecycle of the JVM process.
     */
    public ServiceSupervisor.ShutdownConfigStage<UpstartService> buildServiceSupervisor() {
      return ServiceSupervisor.forService(this::build);
    }

    /**
     * Builds an {@link UpstartService} for the provided {@link #installModule Modules}
     *
     * @see #buildServiceSupervisor
     */
    public UpstartService build() {
      return buildInjector().getInstance(UpstartService.class);
    }

    /**
     * Builds a Guice {@link Injector} for the provided {@link #installModule Modules}. This is likely only desirable
     * for applications which do not involve a {@link ServiceManager ServiceManager}; otherwise, {@link #buildServiceSupervisor}
     * or {@link #build} may be preferable.
     *
     * @see #buildServiceSupervisor
     * @see #build
     */
    public Injector buildInjector() {
      Injector injector = s_latestInjector = UpstartConfigBinder.withBinder(
              configProvider,
              () -> {
                Stage stage = configProvider.deploymentStage().isProductionLike()
                        ? Stage.PRODUCTION
                        : Stage.DEVELOPMENT;
                try {
                  return Guice.createInjector(stage, modules);
                } catch (RuntimeException e) {
                  throw summarizeCreationErrors(e);
                }
              });

      if (LOG.isInfoEnabled()) {
        LOG.info("Loaded config:\n{}", injector.getInstance(UpstartApplicationConfig.class).describeConfig());
      }
      return injector;
    }

    private static boolean isConfigException(Throwable cause) {
      return (cause instanceof ConfigMappingException);// || (cause instanceof ConfigException) || (cause instanceof JsonProcessingException);
    }

    private static ConfigMappingException summarizeCreationErrors(RuntimeException e) {
      if (e instanceof ConfigMappingException) throw e;

      Stream<Throwable> configIssues = Optionals.asInstance(e, CreationException.class)
              .map(creationException ->
                      creationException.getErrorMessages().stream().map(Message::getCause))
              .orElse(Stream.of(e));
      String configMessage = configIssues.flatMap(t -> Exceptions.causes(t).stream()
                      .filter(Builder::isConfigException)
                      .limit(1)
              )
              .map(Throwable::getMessage)
              .distinct()
              .map(HojackConfigProvider::rewriteTargetResourcePaths)
              .collect(Collectors.joining("\n\n"));

      if (configMessage.isEmpty()) throw e;
      LOG.debug("Configuration error will be summarized; original cause:", e);
      throw new ConfigMappingException("Configuration error(s):\n" + configMessage);
    }

    @ConfigPath("upstart.autoModules")
    abstract static class AutoModules extends UpstartModule {

      private static final Logger LOG = LoggerFactory.getLogger(AutoModules.class);

      private static final List<Module> SERVICELOADER_MODULES = ImmutableList.copyOf(ServiceLoader.load(Module.class));

      abstract boolean enabled();

      abstract Map<Class<? extends Module>, Boolean> install();

      @Override
      public void configure() {
        if (!enabled()) return;

        Stream<? extends Module> installs = PairStream.of(install())
                .filterValues(Boolean::booleanValue)
                .keys()
                .map(Reflect::newInstance);

        List<Module> modules = Stream.concat(installs, SERVICELOADER_MODULES.stream())
                .filter(this::isUnsuppressed)
                .toList();

        if (!modules.isEmpty()) {
          if (LOG.isInfoEnabled()) {
            LOG.info("Installing AutoModules from classpath: [{}]", modules.stream()
                    .map(m -> m.getClass().getName())
                    .collect(Collectors.joining(",")));
          }
          modules.forEach(this::install);
        }
      }

      private boolean isUnsuppressed(Module module) {
        return install().getOrDefault(module.getClass(), Boolean.TRUE);
      }
    }

    public static class UpstartCoreModule extends UpstartModule {
      @Override
      protected void configure() {
        UpstartConfigBinder configBinder = UpstartConfigBinder.get();
        bindConfig(UpstartLogConfig.class).apply();

        UpstartLogProvider.CLASSPATH_PROVIDER.ifPresent(logProvider -> {
          bind(UpstartLogProvider.class).toInstance(logProvider);
        });

        install(bindConfig(AutoModules.class));

        bindConfig("upstart.localhost", LocalHost.class);

        bind(Clock.class).toInstance(Clock.systemUTC());
        bind(Ticker.class).toInstance(Ticker.systemTicker());
        bind(FileSystem.class).toInstance(FileSystems.getDefault());

        UpstartDeploymentStage upstartDeploymentStage = bindConfig(UpstartContext.class).deploymentStage();
        bind(UpstartDeploymentStage.class).toInstance(upstartDeploymentStage);
        bind(UpstartApplicationConfig.class).toProvider(configBinder::finalUpstartConfig).in(Scopes.SINGLETON);
        ManagedServicesModule.init(binder());
      }
    }
  }
}
