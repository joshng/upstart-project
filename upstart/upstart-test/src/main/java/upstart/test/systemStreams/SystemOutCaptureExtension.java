package upstart.test.systemStreams;

import org.junit.jupiter.api.extension.BeforeEachCallback;
import upstart.test.ExtensionContexts;
import upstart.test.SetupPhase;
import upstart.test.SingletonParameterResolver;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeTestExecutionCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

public class SystemOutCaptureExtension extends SingletonParameterResolver<SystemOutCaptor>
        implements BeforeEachCallback, BeforeTestExecutionCallback, AfterEachCallback {
  public SystemOutCaptureExtension() {
    super(SystemOutCaptor.class);
  }

  @Override
  public void beforeEach(ExtensionContext context) throws Exception {
    maybeStart(context, SetupPhase.BeforeEach);
  }

  @Override
  public void beforeTestExecution(ExtensionContext context) throws Exception {
    maybeStart(context, SetupPhase.BeforeTestExecution);
  }

  private void maybeStart(ExtensionContext context, SetupPhase currentPhase) {
    SetupPhase startPhase = ExtensionContexts.findNearestAnnotation(
                    CaptureSystemOut.class,
                    context
            ).map(CaptureSystemOut::value)
            .orElse(SetupPhase.BeforeTestExecution);
    if (currentPhase == startPhase) getOrCreateContext(context).startCapture();
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
