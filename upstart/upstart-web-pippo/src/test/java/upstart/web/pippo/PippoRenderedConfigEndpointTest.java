package upstart.web.pippo;

import upstart.web.test.BaseRenderedConfigEndpointTest;

public class PippoRenderedConfigEndpointTest extends BaseRenderedConfigEndpointTest {
  @Override
  protected void configureConfigEndpoint() {
    install(RenderedConfigEndpoint.Module.class);
  }
}
