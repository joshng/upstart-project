package upstart.web.javalin;

import upstart.javalin.RenderedConfigEndpoint;
import upstart.web.test.BaseRenderedConfigEndpointTest;

public class JavalinRenderedConfigEndpointTest extends BaseRenderedConfigEndpointTest {
  @Override
  protected void configureConfigEndpoint() {
    install(RenderedConfigEndpoint.Module.class);
  }
}
