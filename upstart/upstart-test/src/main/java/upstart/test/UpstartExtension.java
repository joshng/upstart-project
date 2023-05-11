package upstart.test;

import com.google.common.base.Preconditions;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.inject.Injector;
import com.google.inject.Module;
import upstart.InternalTestBuilder;
import upstart.UpstartStaticInitializer;
import upstart.config.EnvironmentConfigExtension;
import upstart.config.EnvironmentConfigFixture;
import upstart.config.UpstartEnvironment;
import upstart.util.collect.MoreStreams;
import upstart.util.reflect.MultiMethodInvoker;
import upstart.util.reflect.Reflect;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeTestExecutionCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.mockito.Mockito;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static java.util.function.Predicate.*;

/**
 * @see UpstartTest
 */
public class UpstartExtension extends SingletonParameterResolver<UpstartTestBuilder>
        implements BeforeTestExecutionCallback, AfterEachCallback {

  static {
//    System.setProperty("config.trace", "loads"); // uncomment to see verbose config-loading spew
    configureTestEnvironment();
    UpstartStaticInitializer.ensureInitialized();
  }

  public static void ensureInitialized() {

  }

  private static final MultiMethodInvoker.Dispatcher<Object> AFTER_INJECTION_DISPATCHER =
          MultiMethodInvoker.machingMethodDispatcher(Reflect.annotationPredicate(AfterInjection.class));

  UpstartExtension() {
    super(UpstartTestBuilder.class);
  }

  public static void installTestModules(ExtensionContext context, UpstartTestBuilder builder) {
    UpstartTestInitializer.installAnnotatedModule(UpstartTest.class, UpstartTest::value, builder, context);

    ExtensionContexts.findRepeatableTestAnnotations(
                    UpstartTestAnnotation.class,
                    Reflect.LineageOrder.SuperclassBeforeSubclass,
                    context
            ).map(UpstartTestAnnotation::value)
            .distinct()
            .map(Reflect::newInstance)
            .forEach(initializer -> initializer.initialize(builder, context));

    MoreStreams.filter(ExtensionContexts.allNestedTestInstances(context).stream(), Module.class)
            .forEach(builder::installModule);
  }

  public static Optional<? extends UpstartTestBuilder> getOptionalTestBuilder(ExtensionContext extensionContext) {
    return getOrCreateOptionalContext(UpstartTestBuilder.class, extensionContext);
  }

  public static void applyOptionalEnvironmentValues(ExtensionContext extensionContext, EnvironmentConfigFixture fixture) {
    getOptionalTestBuilder(extensionContext).ifPresent(config -> fixture.applyEnvironmentValues(config, Optional.of(extensionContext)));
  }

  public static UpstartTestBuilder getRequiredTestBuilder(ExtensionContext extensionContext) {
    return getOptionalTestBuilder(extensionContext).orElseThrow(() -> new IllegalStateException("No UpstartTest found"));
  }

  @Override
  protected InternalTestBuilder createContext(ExtensionContext extensionContext) throws Exception {
    Preconditions.checkState(extensionContext.getTestInstance().isPresent(),
            "UpstartTestBuilder is not supported in static scope (@BeforeAll), use @BeforeEach instead");
    return new InternalTestBuilder(EnvironmentConfigExtension.getConfigBuilder(extensionContext));
  }

  public static void configureTestEnvironment() {
    System.setProperty(UpstartEnvironment.UPSTART_ENVIRONMENT, UpstartTest.TEST_ENVIRONMENT_NAME);
  }

  @Override
  public void beforeTestExecution(ExtensionContext context) throws Exception {
    InternalTestBuilder configurator = (InternalTestBuilder) getOrCreateContext(context);

    installTestModules(context, configurator);

    Injector injector = configurator.getInjector();
//    GraphvizGrapher grapher = injector.getInstance(GraphvizGrapher.class);
//    grapher.setRankdir("TB");
//    grapher.setOut(new PrintWriter(System.out));
//    grapher.graph(injector);

    ExtensionContexts.allNestedTestInstances(context).forEach(instance -> {
      injector.injectMembers(instance);

      AFTER_INJECTION_DISPATCHER.dispatch(instance, false);
    });

    configurator.invokeAfterInjection();
  }

  @Override
  public void afterEach(ExtensionContext context) throws Exception {
    Mockito.validateMockitoUsage();
  }
}
