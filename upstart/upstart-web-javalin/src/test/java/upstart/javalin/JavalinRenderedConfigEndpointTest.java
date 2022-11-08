package upstart.javalin;

import upstart.web.test.BaseRenderedConfigEndpointTest;

public class JavalinRenderedConfigEndpointTest extends BaseRenderedConfigEndpointTest implements JavalinWebModule {
  @Override
  protected void configureConfigEndpoint() {
    install(new RenderedConfigEndpoint.Module());
    addJavalinWebBinding().toInstance(config -> config.accessManager((handler, ctx, routeRoles) -> handler.handle(ctx)));
  }
}
