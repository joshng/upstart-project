package upstart.aws.test.dynamodb;

import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import upstart.test.SingletonParameterResolver;
import upstart.test.UpstartExtension;

public class LocalDynamoDBExtension extends SingletonParameterResolver<DynamoDBFixture> implements BeforeEachCallback, AfterEachCallback {
  public LocalDynamoDBExtension() {
    super(DynamoDBFixture.class);
  }

  @Override
  public void afterEach(ExtensionContext extensionContext) throws Exception {
    getExistingContext(extensionContext).ifPresent(dynamoDBFixture -> dynamoDBFixture.stop().join());
  }

  @Override
  public void beforeEach(ExtensionContext extensionContext) throws Exception {
    DynamoDBFixture fixture = getOrCreateContext(extensionContext);
    UpstartExtension.getOptionalTestBuilder(extensionContext)
            .ifPresent(testBuilder -> testBuilder.overrideConfig("upstart.aws.dynamodb.endpoint", fixture.endpoint()));
  }

  @Override
  protected DynamoDBFixture createContext(ExtensionContext extensionContext) throws Exception {
    DynamoDBFixture fixture = new DynamoDBFixture();
    fixture.start().join();
    return fixture;
  }
}
