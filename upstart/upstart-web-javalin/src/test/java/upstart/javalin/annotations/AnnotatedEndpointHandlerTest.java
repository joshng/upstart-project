package upstart.javalin.annotations;

import io.javalin.core.security.RouteRole;
import io.javalin.http.Context;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.List;

import static com.google.common.truth.Truth.assertThat;

class AnnotatedEndpointHandlerTest {
  private static final HttpRegistry<Require> REGISTRY = new HttpRegistry<>(Require.class, Require::value);

  @Test
  void tryAnnotations() {
    HttpRoutes<AnnotatedEndpointHandlerTest> routes = REGISTRY.getRoutes(
            HttpUrl.of("/context-path/"),
            AnnotatedEndpointHandlerTest.class
    );

    assertThat(routes.urlFor(proxy -> proxy.getThing("magic-value", null, null)).value()).isEqualTo("/context-path/things/magic-value");
  }


  @Http(verb = Http.Verb.GET, path = "/things/{b}" )
  @Require(TestRole.User)
  public void getThing(@PathParam String b, List<String> vegetables, Context ctx) {

  }

  @Retention(RetentionPolicy.RUNTIME)
  @Http.AccessControlAnnotation
  @interface Require {
    TestRole[] value();
  }

  enum TestRole implements RouteRole {
    Anonymous,
    User,
    Admin;
  }
}
