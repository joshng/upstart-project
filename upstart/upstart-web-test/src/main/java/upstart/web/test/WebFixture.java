package upstart.web.test;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import upstart.test.AvailablePortAllocator;
import upstart.test.UpstartTestBuilder;
import upstart.web.WebServerConfig;
import io.restassured.specification.RequestSpecification;

import javax.inject.Inject;

import static io.restassured.RestAssured.given;

public class WebFixture {
  private final RequestSpecification requestSpecification;
  private final int port;

  static {
    RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
  }

  @Inject
  WebFixture(WebServerConfig config) {
    this(config.port());
  }

  public WebFixture(int port) {
    requestSpecification = given().port(port);
    this.port = port;
  }

  public int getPort() {
    return port;
  }

  public RequestSpecification request() {
    return given(requestSpecification);
  }

  public RequestSpecification requestJson() {
    return given(requestSpecification).accept(ContentType.JSON);
  }

  public static void configureRandomPort(UpstartTestBuilder testBuilder) {
    testBuilder.apply(builder -> {
      int port = AvailablePortAllocator.allocatePort();
      builder.overrideConfig("upstart.web.server.port", port);
    });
  }
}
