package upstart.test;

import com.google.common.base.Preconditions;
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

import java.util.Optional;

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
    ExtensionContexts.findTestAnnotations(UpstartTest.class, Reflect.LineageOrder.SuperclassBeforeSubclass, context)
            .map(UpstartTest::value)
            .filter(not(isEqual(Module.class)))
            .forEach(builder::installModule);

    MoreStreams.filter(ExtensionContexts.allNestedTestInstances(context).stream(), Module.class)
            .forEach(builder::installModule);
  }

  public static Optional<? extends UpstartTestBuilder> getOptionalTestBuilder(ExtensionContext extensionContext) {
    return getOrCreateOptionalContext(UpstartTestBuilder.class, extensionContext);
  }

  public static void applyOptionalEnvironmentValues(ExtensionContext extensionContext, EnvironmentConfigFixture fixture) {
    getOptionalTestBuilder(extensionContext).ifPresent(fixture::applyEnvironmentValues);
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

  public static String configureTestEnvironment() {
    return System.setProperty(UpstartEnvironment.UPSTART_ENVIRONMENT, "TEST");
  }

  @Override
  public void beforeTestExecution(ExtensionContext context) throws Exception {
    InternalTestBuilder configurator = (InternalTestBuilder) getOrCreateContext(context);

    installTestModules(context, configurator);

    Injector injector = configurator.getInjector();

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
