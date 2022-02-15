package upstart.web.test;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.ImmutableMap;
import io.restassured.response.Response;
import upstart.config.annotations.ConfigPath;
import upstart.config.UpstartModule;
import upstart.test.UpstartServiceTest;
import upstart.test.UpstartTestBuilder;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;

import static com.google.common.truth.Truth.assertThat;

@UpstartServiceTest
public abstract class BaseRenderedConfigEndpointTest extends UpstartModule {
  public static final String VALUE_CONFIG_PATH = MyConfig.CONFIG_PATH + ".value";
  private static final String URI = "/my-config";
  private static final int EXPECTED_VALUE = 55;
  @Inject
  WebFixture web;

  @Override
  protected void configure() {
    configureConfigEndpoint();
    bindConfig(MyConfig.class);
  }

  protected abstract void configureConfigEndpoint();

  @BeforeEach
  void setupConfig(UpstartTestBuilder builder) {
    WebFixture.configureRandomPort(builder);
    builder.overrideConfig(VALUE_CONFIG_PATH, EXPECTED_VALUE)
            .overrideConfig("upstart.configEndpoint.uri", URI);
  }

  @Test
  void renderedConfigRenders() {
    String result = web.request().get(URI).body().asString();
    Config config = ConfigFactory.parseString(result);
    assertThat(config.getInt(VALUE_CONFIG_PATH)).isEqualTo(EXPECTED_VALUE);
  }

  @ConfigPath(BaseRenderedConfigEndpointTest.MyConfig.CONFIG_PATH)
  public interface MyConfig {
    static final String CONFIG_PATH = "renderedEndpointConfig.test";

    public int value();
  }

  @Nested
  class WithJsonResponse {
    @BeforeEach
    void setupConfig(UpstartTestBuilder builder) {
      builder.overrideConfig("upstart.configEndpoint.renderOptions",
              ImmutableMap.of("json", true, "originComments", false));
    }

    @Test
    void jsonRenders() {
      ObjectNode result = web.request().get(URI).as(ObjectNode.class);
      assertThat(result.get("renderedEndpointConfig").get("test").get("value").asInt()).isEqualTo(EXPECTED_VALUE);
    }
  }
}
