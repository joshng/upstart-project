package upstart.cluster.test;

import com.google.inject.Injector;
import upstart.services.UpstartService;
import upstart.cluster.zk.ClusterId;
import upstart.InternalTestBuilder;
import upstart.config.EnvironmentConfigBuilder;
import upstart.test.UpstartTestBuilder;
import upstart.test.SingletonExtension;
import upstart.test.proxy.RecordingBuilderProxy;
import upstart.util.concurrent.FutureSuccessTracker;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import static com.google.common.base.Preconditions.checkState;

public class UpstartTestClusterBuilder {
  private int initialNodeCount;
  private final EnvironmentConfigBuilder configBuilder;
  private final RecordingBuilderProxy<UpstartTestBuilder> allNodeBuilder = newNodeBuilder();
  private final Map<Integer, RecordingBuilderProxy<UpstartTestBuilder>> specificBuilderInvocations = new HashMap<>();
  private final FutureSuccessTracker serviceSuccessTracker = new FutureSuccessTracker();
  private final Set<ClusterId> clusterIds = new HashSet<>();
  private final List<UpstartService> applications = new ArrayList<>();
  private final AtomicBoolean builtInitialNodes = new AtomicBoolean(false);

  UpstartTestClusterBuilder(int initialNodeCount, EnvironmentConfigBuilder configBuilder) {
    this.initialNodeCount = initialNodeCount;
    this.configBuilder = configBuilder;
  }

  public CompletableFuture<?> servicesStoppedFuture() {
    return serviceSuccessTracker.completionFuture();
  }

  public Stream<UpstartService> getNodes() {
    return applications.stream();
  }

  void awaitShutdown(Duration duration) throws InterruptedException, ExecutionException, TimeoutException {
    serviceSuccessTracker.setNoMoreJobs().get(duration.toMillis(), TimeUnit.MILLISECONDS);
  }

  public int getInitialNodeCount() {
    return initialNodeCount;
  }

  public void setInitialNodeCount(int initialNodeCount) {
    this.initialNodeCount = initialNodeCount;
  }

  public Set<ClusterId> getClusterIds() {
    return clusterIds;
  }

  public UpstartTestBuilder nodeBuilder(int nodeId) {
    return specificBuilderInvocations.computeIfAbsent(nodeId, ignored -> newNodeBuilder()).proxy();
  }

  void buildInitialNodes() {
    checkState(builtInitialNodes.compareAndSet(false, true));
    for (int i = 0; i < initialNodeCount; i++) {
      buildNode(i);
    }
  }

  public UpstartService getNode(int nodeIdx) {
    return applications.get(nodeIdx);
  }

  public UpstartService buildNode(int nodeIdx) {
    InternalTestBuilder builder = new InternalTestBuilder(configBuilder.copy());
    allNodeBuilder.replay(builder);
    builder.overrideConfig("upstart.localhost.hostname", String.format("node-%02d", nodeIdx));
    builder.overrideConfig("upstart.localhost.ipAddress", String.format("127.0.0.%d", nodeIdx));
    Optional.ofNullable(specificBuilderInvocations.get(nodeIdx)).ifPresent(proxy -> proxy.replay(builder));
    builder.invokeAfterInjection();
    Injector injector = builder.getInjector();
    UpstartService app = injector.getInstance(UpstartService.class);
    clusterIds.add(injector.getInstance(ClusterId.class));
    serviceSuccessTracker.track(app.getStoppedFuture());
    applications.add(app);
    return app;
  }

  public UpstartService startAdditionalNode(int nodeIdx) {
    UpstartService application = buildNode(nodeIdx);
    application.start();
    return application;
  }

  public void sleep(int milliseconds) throws ExecutionException, InterruptedException {
    try {
     servicesStoppedFuture().get(milliseconds, TimeUnit.MILLISECONDS);
    } catch (TimeoutException e) {
      // this is expected, we were really just sleeping anyway
    }
  }

  public void waitFor(int maxWaitMilliseconds, Callable<Boolean> condition) throws Exception {
    Instant deadline = Instant.now().plus(maxWaitMilliseconds, ChronoUnit.MILLIS);
    while (!condition.call()) {
      sleep(100);
      if (deadline.isBefore(Instant.now())) throw new TimeoutException("Timed out while waiting for condition");
    }
  }

  public static UpstartTestClusterBuilder getInstance(ExtensionContext context) {
    return SingletonExtension.getOrCreateContextFrom(UpstartClusterExtension.class, context);
  }

  private RecordingBuilderProxy<UpstartTestBuilder> newNodeBuilder() {
    return RecordingBuilderProxy.newProxy(UpstartTestBuilder.class);
  }

  public UpstartTestBuilder allNodes() {
    return allNodeBuilder.proxy();
  }
}
