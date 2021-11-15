package upstart.test.systemStreams;

import upstart.test.ExtensionContexts;
import upstart.test.SingletonParameterResolver;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeTestExecutionCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

public class SystemOutCaptureExtension extends SingletonParameterResolver<SystemOutCaptor> implements BeforeTestExecutionCallback, AfterEachCallback {
  public SystemOutCaptureExtension() {
    super(SystemOutCaptor.class);
  }

  @Override
  public void beforeTestExecution(ExtensionContext extensionContext) throws Exception {
    if (shouldAutoStart(extensionContext)) getOrCreateContext(extensionContext).startCapture();
  }

  public boolean shouldAutoStart(ExtensionContext extensionContext) {
    return ExtensionContexts.findNearestAnnotation(
            CaptureSystemOut.class,
            extensionContext
    ).map(CaptureSystemOut::autoStart)
            .orElse(Boolean.TRUE);
  }

  @Override
  public void afterEach(ExtensionContext extensionContext) throws Exception {
    getExistingContext(extensionContext).ifPresent(SystemOutCaptor::endCapture);
  }

  @Override
  protected SystemOutCaptor createContext(ExtensionContext extensionContext) throws Exception {
    return new SystemOutCaptor();
  }
}
