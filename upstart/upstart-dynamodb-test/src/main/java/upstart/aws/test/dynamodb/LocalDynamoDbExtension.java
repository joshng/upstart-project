package upstart.aws.test.dynamodb;

import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import upstart.test.SingletonExtension;
import upstart.test.UpstartExtension;

public class LocalDynamoDbExtension extends SingletonExtension<DynamoDbFixture> implements BeforeEachCallback, AfterEachCallback, ParameterResolver {
  public LocalDynamoDbExtension() {
    super(DynamoDbFixture.class);
  }

  @Override
  public void afterEach(ExtensionContext extensionContext) throws Exception {
    getExistingContext(extensionContext).ifPresent(dynamoDbFixture -> dynamoDbFixture.stop().join());
  }

  @Override
  public void beforeEach(ExtensionContext extensionContext) throws Exception {
    DynamoDbFixture fixture = getOrCreateContext(extensionContext);
    UpstartExtension.getOptionalTestBuilder(extensionContext)
            .ifPresent(testBuilder -> testBuilder.overrideConfig("upstart.aws.dynamodb.endpoint", fixture.endpoint()));
  }

  @Override
  protected DynamoDbFixture createContext(ExtensionContext extensionContext) throws Exception {
    DynamoDbFixture fixture = new DynamoDbFixture();
    fixture.start().join();
    return fixture;
  }

  @Override
  public boolean supportsParameter(
          ParameterContext parameterContext, ExtensionContext extensionContext
  ) throws ParameterResolutionException {
    return parameterContext.getParameter().getType() == DynamoDbClient.class;
  }

  @Override
  public Object resolveParameter(
          ParameterContext parameterContext, ExtensionContext extensionContext
  ) throws ParameterResolutionException {
    return getOrCreateContext(extensionContext).client();
  }
}
