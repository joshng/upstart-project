package upstart.aws.test.dynamodb;

import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import upstart.dynamodb.DynamoTableInitializer;
import upstart.provisioning.ProvisionedResource;
import upstart.test.BaseSingletonExtension;
import upstart.test.SingletonServiceExtension;
import upstart.test.UpstartExtension;
import upstart.util.reflect.Reflect;

import javax.inject.Named;
import java.util.Optional;

public class LocalDynamoDbExtension extends BaseSingletonExtension<DynamoDbFixture> implements SingletonServiceExtension<DynamoDbFixture>, ParameterResolver {
  public LocalDynamoDbExtension() {
    super(DynamoDbFixture.class);
  }

  @Override
  public DynamoDbFixture createService(ExtensionContext extensionContext) throws Exception {
    UpstartExtension.getOptionalTestBuilder(extensionContext)
            .ifPresent(testBuilder -> testBuilder.installModule(binder -> ProvisionedResource
                    .provisionAtStartup(binder, DynamoTableInitializer.PROVISIONED_RESOURCE_TYPE)));

    return new DynamoDbFixture();
  }

  @Override
  public boolean supportsParameter(
          ParameterContext parameterContext, ExtensionContext extensionContext
  ) throws ParameterResolutionException {
    Class<?> type = parameterContext.getParameter().getType();
    return type == DynamoDbClient.class || type == DynamoDbTable.class || type == DynamoDbFixture.class;
  }

  @Override
  public Object resolveParameter(
          ParameterContext parameterContext, ExtensionContext extensionContext
  ) throws ParameterResolutionException {
    var fixture = getOrCreateContext(extensionContext);
    Class<?> paramType = parameterContext.getParameter().getType();
    if (paramType == DynamoDbTable.class) {
      return Optional.ofNullable(parameterContext.getParameter().getAnnotation(Named.class))
              .map(named -> {
                var beanType = (Class<?>) Reflect.getFirstGenericType(parameterContext.getParameter().getParameterizedType());
                return fixture.enhancedClient().table(named.value(), DynamoTableInitializer.getTableSchema(beanType));
              }).orElseThrow(() -> new IllegalArgumentException("DynamoDbTable<> parameter is missing @Named annotation: " + parameterContext.getParameter().getName()));
    } else if (paramType == DynamoDbClient.class) {
      return fixture.client();
    } else if (paramType == DynamoDbFixture.class) {
      return fixture;
    } else {
      throw new IllegalArgumentException("Unsupported parameter type: " + paramType);
    }
  }
}
