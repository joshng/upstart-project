package upstart.javalin;

import io.javalin.core.security.AccessManager;
import io.javalin.core.security.RouteRole;
import io.javalin.http.Context;
import io.javalin.http.Handler;
import org.jetbrains.annotations.NotNull;
import upstart.javalin.JavalinWebModule;
import upstart.javalin.RenderedConfigEndpoint;
import upstart.web.test.BaseRenderedConfigEndpointTest;

import java.util.Set;

public class JavalinRenderedConfigEndpointTest extends BaseRenderedConfigEndpointTest implements JavalinWebModule {
  @Override
  protected void configureConfigEndpoint() {
    install(RenderedConfigEndpoint.Module.class);
    addJavalinWebBinding().toInstance(config -> config.accessManager((handler, ctx, routeRoles) -> handler.handle(ctx)));
  }
}
