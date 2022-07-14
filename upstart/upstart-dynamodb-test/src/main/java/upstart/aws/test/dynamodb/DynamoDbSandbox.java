package upstart.aws.test.dynamodb;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import upstart.UpstartApplication;
import upstart.UpstartService;
import upstart.config.HojackConfigProvider;
import upstart.config.UpstartEnvironment;

@LocalDynamoDbTest
@Disabled
public abstract class DynamoDbSandbox {
  @Test
  void test(DynamoDbFixture fixture) {
    System.setProperty("UPSTART_ENVIRONMENT", "DEV");
    Config overrideConfig = ConfigFactory
            .parseString("upstart.aws.dynamodb.endpoint: \"%s\"".formatted(fixture.endpoint()));
    HojackConfigProvider configProvider = UpstartEnvironment.of("DEV", ClassLoader.getSystemClassLoader())
            .configProvider().withOverrideConfig(overrideConfig);
    UpstartApplication application = getApplication();
    application.configureSupervisor(UpstartService.builder(configProvider).installModule(application).buildServiceSupervisor()).startAndAwaitTermination();
  }

  protected abstract UpstartApplication getApplication();
}
