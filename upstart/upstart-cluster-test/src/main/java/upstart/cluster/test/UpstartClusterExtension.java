package upstart.cluster.test;

import upstart.UpstartService;
import upstart.config.EnvironmentConfigExtension;
import upstart.test.ExtensionContexts;
import upstart.test.UpstartExtension;
import upstart.test.SingletonParameterResolver;
import upstart.util.concurrent.CompletableFutures;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.AfterTestExecutionCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.BeforeTestExecutionCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

public class UpstartClusterExtension extends SingletonParameterResolver<UpstartTestClusterBuilder>
        implements BeforeEachCallback, BeforeTestExecutionCallback, AfterTestExecutionCallback, AfterEachCallback {
  private static final Logger LOG = LoggerFactory.getLogger(UpstartClusterExtension.class);

  public UpstartClusterExtension() {
    super(UpstartTestClusterBuilder.class);
  }

  @Override
  public void beforeEach(ExtensionContext context) throws Exception {
    UpstartTestClusterBuilder configurator = getOrCreateContext(context);
    ZookeeperFixture.getInstance(context).setupUpstartClusterConfig(configurator.allNodes());
  }

  @Override
  public void beforeTestExecution(ExtensionContext context) throws Exception {
    UpstartTestClusterBuilder configurator = getOrCreateContext(context);

    UpstartExtension.installTestModules(context, configurator.allNodes());

    configurator.buildInitialNodes();

    invokeServiceGraphs(configurator, UpstartService::start);
  }

  @Override
  public void afterTestExecution(ExtensionContext context) throws Exception {
    invokeServiceGraphs(getOrCreateContext(context), UpstartService::stop);
  }

  @Override
  public void afterEach(ExtensionContext context) {
    Mockito.validateMockitoUsage();
  }

  private UpstartTestClusterBuilder invokeServiceGraphs(UpstartTestClusterBuilder configurator, Function<UpstartService, CompletableFuture<?>> invocation) throws InterruptedException, java.util.concurrent.ExecutionException, java.util.concurrent.TimeoutException {
    CompletableFutures.allOf(configurator.getNodes().map(invocation)).get(20, TimeUnit.SECONDS);
    return configurator;
  }

  @Override
  protected UpstartTestClusterBuilder createContext(ExtensionContext extensionContext) throws Exception {
    UpstartExtension.configureTestEnvironment();
    int nodeCount = ExtensionContexts.findNearestAnnotation(UpstartClusterTest.class, extensionContext)
            .map(UpstartClusterTest::nodeCount)
            .orElse(UpstartClusterTest.DEFAULT_NODE_COUNT);
    return new UpstartTestClusterBuilder(nodeCount, EnvironmentConfigExtension.getConfigBuilder(extensionContext));
  }
}
