package upstart.web.test;

import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import upstart.test.AvailablePortAllocator;
import upstart.test.ExtensionContexts;
import upstart.test.SingletonParameterResolver;
import upstart.test.UpstartExtension;

public class WebExtension extends SingletonParameterResolver<WebFixture> implements BeforeEachCallback {
  static final int RANDOM_PORT = -1;

  protected WebExtension() {
    super(WebFixture.class);
  }

  @Override
  public void beforeEach(ExtensionContext extensionContext) {
    getOrCreateContext(extensionContext)
            .configurePort(UpstartExtension.getOptionalTestBuilder(extensionContext).orElseThrow());
  }

  @Override
  protected WebFixture createContext(ExtensionContext extensionContext) throws Exception {
    return new WebFixture(ExtensionContexts.findNearestAnnotation(WebTest.class, extensionContext)
                                  .map(WebTest::port)
                                  .filter(n -> n != RANDOM_PORT)
                                  .orElseGet(AvailablePortAllocator::allocatePort));
  }
}
