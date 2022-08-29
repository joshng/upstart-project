package upstart.javalin;

import upstart.web.test.BaseRenderedConfigEndpointTest;

public class JavalinRenderedConfigEndpointTest extends BaseRenderedConfigEndpointTest implements JavalinWebModule {
  @Override
  protected void configureConfigEndpoint() {
    install(RenderedConfigEndpoint.Module.class);
    addJavalinWebBinding().toInstance(config -> config.accessManager((handler, ctx, routeRoles) -> handler.handle(ctx)));
  }
}
