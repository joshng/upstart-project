package upstart.log4j.test;

import upstart.test.ExtensionContexts;
import upstart.test.SingletonParameterResolver;
import upstart.util.reflect.Reflect;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * Allows fine-tuning log-thresholds for specific tests, without impacting other tests in the same process.<p/>
 *
 * Most scenarios can use the {@link SuppressLogs} annotation to apply settings to entire test-methods or classes,
 * but use-cases that benefit from adjusting thresholds at specific times may also request a {@link LogFixture}
 * and invoke its methods directly:
 *
 * <pre>{@code
 * @ExtendWith(LogExtension.class)
 * class MyTest {
 *   Logger LOG = LogFactory.getLogger(MyTest.class);
 *
 *   @Test
 *   void adjustLogs(LogFixture logFixture) {
 *     LOG.warn("This is logged because the defaults enable WARN");
 *
 *     logFixture.setThreshold(MyTest.class, Slf4jConfig.LogThreshold.ERROR);
 *
 *     LOG.warn("This is NOT logged because we've adjusted the threshold on the previous line");
 *   }
 *
 *   @Test
 *   void unadjustedLogs() {
 *     LOG.warn("This is logged because LogFixture resets the thresholds after each test");
 *   }
 *
 *   @SuppressLogs(MyTest.class)
 *   @Test
 *   void annotatedAdjustment() {
 *     LOG.error("This is not logged because of the @SuppressLogs annotation");
 *   }
 *
 * }</pre>
 */
public class LogExtension extends SingletonParameterResolver<LogFixture> implements BeforeEachCallback, AfterEachCallback {
  protected LogExtension() {
    super(LogFixture.class);
  }

  @Override
  public void beforeEach(ExtensionContext context) throws Exception {
    LogFixture fixture = getOrCreateContext(context);
    ExtensionContexts.findRepeatableTestAnnotations(SuppressLogs.class, Reflect.LineageOrder.SuperclassBeforeSubclass, context)
            .forEach(fixture::apply);
  }

  @Override
  public void afterEach(ExtensionContext context) throws Exception {
    getExistingContext(context).ifPresent(LogFixture::revert);
  }

  @Override
  protected LogFixture createContext(ExtensionContext extensionContext) throws Exception {
    return new LogFixture();
  }
}
