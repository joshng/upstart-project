package upstart.javalin.annotations;

import io.javalin.core.security.RouteRole;
import io.javalin.http.Context;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import upstart.config.UpstartModule;
import upstart.javalin.JavalinWebModule;
import upstart.test.UpstartServiceTest;
import upstart.test.UpstartTestBuilder;
import upstart.web.test.WebTest;

import javax.inject.Inject;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.List;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HttpRoutesTest {
  static {
    HttpRegistry.INSTANCE.registerRequiredRoleAnnotation(Require.class, Require::value);
  }

  @Retention(RetentionPolicy.RUNTIME)
  @Http.AccessControlAnnotation
  @interface Require {
    TestRole[] value();
  }

  static class TestEndpoints {
    @Http(method = Http.Method.POST, path = "/things/{thing_id}")
    @Require(TestRole.User)
    public void getThing(@PathParam String thingId, List<String> vegetables, Context ctx) {

    }
  }

  @Test
  void annotationRouteGeneration() {
    // upstart applications can just @Install HttpRoutes<T>
    HttpRoutes<TestEndpoints> routes = HttpRegistry.INSTANCE.getRoutes(
            HttpUrl.of("/context-path/"),
            TestEndpoints.class
    );

    assertThat(routes.urlFor(proxy -> proxy.getThing("magic-value", null, null)).value())
            .isEqualTo("/context-path/things/magic-value");
  }

  @Test
  void nonRouteParametersAreRejected() {
    HttpRoutes<TestEndpoints> routes = HttpRegistry.INSTANCE.getRoutes(
            HttpUrl.of("/context-path/"),
            TestEndpoints.class
    );

    IllegalArgumentException e = assertThrows(
            IllegalArgumentException.class,
            () -> routes.urlFor(web -> web.getThing("thing-id", List.of(), null))
    );
    assertThat(e).hasMessageThat().contains("vegetables");
  }

  @Nested
  @UpstartServiceTest
  @WebTest
  class WithUpstartApp extends UpstartModule implements JavalinWebModule {
    @Override
    protected void configure() {
      serveHttp(TestEndpoints.class);
    }

    @BeforeEach
    void setContextPath(UpstartTestBuilder testBuilder) {
      testBuilder.overrideConfig("upstart.web.server.contextPath", "/pumpkin");
    }

    @Inject
    HttpRoutes<TestEndpoints> routes;

    @Test
    void injectedRoutes() {
      assertThat(routes.urlFor(proxy -> proxy.getThing("magic-value", null, null)).value())
              .isEqualTo("/pumpkin/things/magic-value");
    }
  }


  enum TestRole implements RouteRole {
    Anonymous,
    User,
    Admin;
  }
}
