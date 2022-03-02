package upstart.b4;

import com.google.common.graph.GraphBuilder;
import com.google.common.graph.ImmutableGraph;
import com.google.common.graph.MutableGraph;
import com.google.common.util.concurrent.Service;
import com.google.inject.util.Modules;
import upstart.services.UpstartService;
import upstart.b4.config.TargetRegistry;
import upstart.b4.config.TargetConfigurator;
import upstart.config.HojackConfigProvider;
import upstart.util.MoreCollectors;
import upstart.util.MoreFunctions;
import upstart.util.MoreStreams;
import upstart.util.PairStream;
import upstart.util.exceptions.Exceptions;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigRenderOptions;
import org.immutables.value.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class B4Application {
  private static final Logger LOG = LoggerFactory.getLogger(B4Application.class);

  private final UpstartService upstartService;
  private final Map<TargetInstanceId, TargetExecutionConfig> rootExecutionConfigs;
  private final Predicate<TargetConfigurator> includeTargetConfigs;
  private final Predicate<TargetConfigurator> executeTarget;
  private final Config targetConfig;
  private final TargetRegistry registry;
  private final Map<TargetInstanceId, TargetInvocation> activeInvocations;
  private final TargetInvocationGraph invocationGraph;
  private final B4GraphDriver driver;
  private final ExecutionConfig baseExecutionConfig;

  public B4Application(Collection<TargetConfigurator> rootCommands, TargetRegistry targetRegistry, ExecutionConfig executionConfig) {
    baseExecutionConfig = executionConfig;
    registry = targetRegistry;
    this.rootExecutionConfigs = PairStream.withMappedKeys(TargetConfigurator.mergeConfigurators(rootCommands.stream()), TargetConfigurator::target)
            .mapValues(TargetExecutionConfig::copyOf)
            .toImmutableMap();
    includeTargetConfigs = MoreFunctions.onResultOf(TargetConfigurator::target, executionConfig.targetConfigInclusion());
    executeTarget = MoreFunctions.onResultOf(TargetConfigurator::target, executionConfig.targetExecutionInclusion().and(executionConfig
            .targetConfigInclusion()));

    Config bootstrapConfig = targetRegistry.config();

    List<TargetConfigurator> activeConfigurators = findActiveTargetConfigurators(rootCommands, executionConfig);

    Config configured = applyConfigurators(bootstrapConfig, activeConfigurators);

    // reapply rootCommand configs, to ensure they get precedence
    Optional<Config> rootOverrides = rootCommands.stream()
            .map(TargetConfigurator::simpleConfig)
            .flatMap(Optional::stream)
            .reduce(B4Application::withOverrides);

    targetConfig = rootOverrides
            .map(overrides -> overrides.withFallback(configured))
            .orElse(configured);

    activeInvocations = activeConfigurators.stream()
            .map(this::buildInvocation)
            .collect(MoreCollectors.toImmutableIndexMap(TargetInvocation::id));

    MutableGraph<TargetInvocation> graph =  GraphBuilder.directed().expectedNodeCount(activeInvocations.size()).build();

    activeInvocations.values().forEach(invocation -> {
      graph.addNode(invocation);
      List<TargetInvocation> deps = findActiveInvocations(invocation.spec().dependencies());
      List<TargetInvocation> cmds = findActiveInvocations(invocation.spec().tasks());
      if (cmds.isEmpty()) {
        deps.forEach(dep -> graph.putEdge(dep, invocation));
      } else {
        PairStream.cartesianProduct(deps.stream(), cmds::stream).forEach((BiConsumer<TargetInvocation, TargetInvocation>) graph::putEdge);
        cmds.forEach(cmd -> graph.putEdge(cmd, invocation));
      }
    });

    if (baseExecutionConfig.pruneTargetGraph()) pruneNoopInvocations(graph);
    // TODO: throw exception for cycles
//    if (Graphs.hasCycle(graph)) {
//    }

    invocationGraph = new TargetInvocationGraph(ImmutableGraph.copyOf(graph));

    Set<B4TargetModule> targetModules = graph.nodes().stream()
            .map(B4TargetModule::new)
            .collect(Collectors.toSet());

    HojackConfigProvider appConfig = HojackConfigProvider.builder().baseConfig(targetConfig).build();

    upstartService = UpstartService.builder(appConfig)
            .installModule(Modules.combine(targetModules))
            .installModule(new B4Module(this))
            .build();

    driver = upstartService.getInstance(B4GraphDriver.class);

    if (LOG.isDebugEnabled()) LOG.debug("Config dump:\n{}", dumpConfigs());
  }

  public ExecutionConfig baseExecutionConfig() {
    return baseExecutionConfig;
  }

  public Optional<TargetInvocation> getInvocation(TargetInstanceId id) {
    return Optional.ofNullable(activeInvocations.get(id));
  }

  /** simply reverses the order of {@link Config#withFallback } */
  private static Config withOverrides(Config a, Config b) {
    return b.withFallback(a);
  }

  private void pruneNoopInvocations(MutableGraph<TargetInvocation> graph) {
    activeInvocations.values().stream()
            .filter(invocation -> invocation.spec().impl().isEmpty())
            .forEach(invocation -> {
              Set<TargetInvocation> predecessors = graph.predecessors(invocation);
              Set<TargetInvocation> successors = graph.successors(invocation);
              if (predecessors.size() == 1 || successors.size() == 1) {
                graph.removeNode(invocation);
                PairStream.cartesianProduct(predecessors.stream(), successors::stream)
                        .forEach((BiConsumer<TargetInvocation, TargetInvocation>) graph::putEdge);
              }
            });
  }

  public static ExecutionConfig defaultExecutionConfig() {
    return ExecutionConfig.DEFAULT;
  }

  public TargetInvocationGraph getInvocationGraph() {
    return invocationGraph;
  }

  private List<TargetConfigurator> findActiveTargetConfigurators(Collection<TargetConfigurator> commands, TargetExecutionConfig config) {
    return TargetConfigurator.mergeConfigurators(
            collectConfigurators(config, commands.stream()).filter(executeTarget)
    ).collect(Collectors.toList());
  }

  public UpstartService getApplication() {
    return upstartService;
  }

  /**
   * Runs the application, waits for it to terminate, and throws an exception if the execution fails.
   */
  public void runOrThrow() {
    startAndAwaitTermination();
    driver.failure().ifPresent(Exceptions::throwUnchecked);
  }

  /**
   * Starts the application, and blocks until it completes.
   * <p/>
   * NOTE that this method ignores the status of the outcome. To expose exceptions related to
   * application failure, see {@link #runOrThrow()} or {@link #failure()}/{@link #failureFuture()}
   */
  public void startAndAwaitTermination() {
    upstartService.startAsync().awaitTerminated();
  }

  public CompletableFuture<Service.State> getStartedFuture() {
    return upstartService.getStartedFuture();
  }

  public CompletableFuture<Service.State> getStoppedFuture() {
    return upstartService.getStoppedFuture();
  }

  public boolean isRunning() {
    return upstartService.isRunning();
  }

  public CompletableFuture<Service.State> start() {
    return upstartService.start();
  }

  public CompletableFuture<Service.State> stop() {
    return upstartService.stop();
  }

  public void awaitTerminated() {
    upstartService.awaitTerminated();
  }

  public void awaitTerminated(long timeout, TimeUnit unit) throws TimeoutException {
    upstartService.awaitTerminated(timeout, unit);
  }

  /**
   * A {@link CompletableFuture} which only completes if the application <em>FAILS</em>. If the application completes
   * normally, any continuations attached to this future are never invoked.
   */
  public CompletableFuture<Throwable> failureFuture() {
    return driver.failureFuture();
  }

  public Optional<Throwable> failure() {
    return driver.failure();
  }

  public String describeExecution(Optional<Function<TargetInvocation, String>> nameFormatter) {
    return invocationGraph.render(nameFormatter.orElse(targetInvocation -> targetInvocation.id().displayName()));
  }

  public String dumpConfigs() {
    return upstartService.config().activeConfig().root().render(ConfigRenderOptions.defaults().setComments(false).setOriginComments(true));
  }

  private Stream<TargetConfigurator> collectConfigurators(TargetExecutionConfig executionConfig, Stream<TargetConfigurator> configurators) {
    // TODO: detect circular deps?
    return configurators.filter(includeTargetConfigs)
            .flatMap(configurator -> {
              TargetConfigurator mergedConfig = TargetConfigurator.builder()
                      .from(executionConfig) // take parent execution-config
                      .from(configurator) // override with declared target values (which cannot specify execution-configs)
                      // finally, override with command-line-specified execution-configs
                      .from(rootExecutionConfigs.getOrDefault(configurator.target(), TargetExecutionConfig.DEFAULT))
                      .build();
              return MoreStreams.prepend(
                      mergedConfig,
                      // recur into sub-targets, using this execution-config
                      collectConfigurators(mergedConfig,
                              registry.targetSpec(configurator.target()).subTargetConfigurators()
                      )
              );
            });
  }

  private TargetInvocation buildInvocation(TargetConfigurator configurator) {
    return TargetInvocation.builder()
            .from(configurator)
            .id(configurator.target())
            .spec(registry.targetSpec(configurator.target()))
            .config(targetConfig)
            .build();
  }

  private Config applyConfigurators(Config globalConfig, Collection<TargetConfigurator> configurators) {
    return configurators.stream()
            .reduce(globalConfig,
                    (config, configurator) -> configurator.configBuilder()
                            .configure(configurator.target().applyReferenceConfig(config)),
                    Config::withFallback
            );
  }

  private List<TargetInvocation> findActiveInvocations(List<TargetConfigurator> targets) {
    return targets.stream()
            .filter(executeTarget)
            .map(TargetConfigurator::target)
            .map(activeInvocations::get)
            .collect(Collectors.toList());
  }

  private static final Pattern LINE_START = Pattern.compile("^", Pattern.MULTILINE);
  public Stream<String> getActiveCommandHelp() {
    return invocationGraph.allInvocations()
            .map(TargetInvocation::id)
            .map(TargetInstanceId::targetName)
            .distinct()
            .sorted(TargetName.LEXICAL_COMPARATOR)
            .map(target -> {
              String targetHelp = registry.getTargetHelp(target);
              String invocationConfigs = activeInvocations.values().stream()
                      .filter(inv -> inv.id().targetName().equals(target) && inv.hasConfigFlags())
                      .map(TargetInvocation::configString)
                      .sorted()
                      .collect(Collectors.joining("\n"));
              if (!invocationConfigs.isEmpty()) targetHelp += "\n@|underline resolved task configs|@\n" + LINE_START.matcher(invocationConfigs).replaceAll("! ");
              return targetHelp;
            });

  }

  public String renderStatusGraph() {
    return driver.renderStatusGraph();
  }

  @Value.Immutable
  public interface ExecutionConfig extends TargetExecutionConfig {
    ExecutionConfig DEFAULT = builder().build();

    ExecutionConfig withPruneTargetGraph(boolean value);

    ExecutionConfig withTargetConfigInclusion(Predicate<TargetInstanceId> value);

    ExecutionConfig withTargetExecutionInclusion(Predicate<TargetInstanceId> value);

    ExecutionConfig withPhases(TargetInvocation.Phases value);

    ExecutionConfig withVerbosity(B4Function.Verbosity value);

    default ExecutionConfig skipTargetsMatching(String... patterns) {
      return withTargetExecutionInclusion(B4Cli.patternPredicate(Arrays.asList(patterns), false));
    }

    default ExecutionConfig skipTargetsMatching(Pattern pattern) {
      return withTargetExecutionInclusion(B4Cli.patternPredicate(pattern, false));
    }

    static ImmutableExecutionConfig.Builder builder() {
      return ImmutableExecutionConfig.builder();
    }

    @Value.Default
    default boolean pruneTargetGraph() {
      return true;
    }

    @Value.Default
    default boolean dryRun() {
      return false;
    }

    @Value.Default
    default Predicate<TargetInstanceId> targetConfigInclusion() {
      return targetInstanceId -> true;
    }

    @Value.Default
    default Predicate<TargetInstanceId> targetExecutionInclusion() {
      return targetInstanceId -> true;
    }
  }
}
