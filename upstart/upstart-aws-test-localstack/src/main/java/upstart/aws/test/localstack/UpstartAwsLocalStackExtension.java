package upstart.aws.test.localstack;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.Streams;
import com.google.inject.multibindings.Multibinder;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.awscore.client.builder.AwsClientBuilder;
import software.amazon.awssdk.awscore.client.builder.AwsSyncClientBuilder;
import software.amazon.awssdk.core.SdkClient;
import software.amazon.awssdk.http.SdkHttpClient;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.regions.Region;
import upstart.aws.AwsClientType;
import upstart.config.TestConfigBuilder;
import upstart.provisioning.ProvisionedResource;
import upstart.test.ExtensionContexts;
import upstart.test.SingletonExtension;
import upstart.test.UpstartExtension;
import upstart.util.concurrent.LazyReference;
import upstart.util.exceptions.MultiException;
import upstart.util.reflect.Reflect;

import java.net.URI;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

public class UpstartAwsLocalStackExtension implements BeforeEachCallback, AfterEachCallback, SingletonExtension<UpstartAwsLocalStackExtension.Fixture>, ParameterResolver {
  @Override
  public void beforeEach(ExtensionContext context) throws Exception {
    getOrCreateContext(context);
  }

  @Override
  public void afterEach(ExtensionContext context) throws Exception {
    getExistingContext(context).ifPresent(Fixture::stop);
  }

  @Override
  public Fixture createContext(ExtensionContext context) {
    LocalStackContainer.Service[] services = localStackAnnotations(context)
            .map(AwsLocalStackTest::value)
            .flatMap(Arrays::stream)
            .distinct()
            .toArray(LocalStackContainer.Service[]::new);

    String imageName = localStackAnnotations(context)
            .map(AwsLocalStackTest::image)
            .filter(s -> !s.isEmpty())
            .findFirst()
            .orElse(AwsLocalStackTest.DEFAULT_LOCALSTACK_IMAGE);

    Fixture fixture = new Fixture(imageName, services);

    UpstartExtension.getOptionalTestBuilder(context)
            .ifPresent(testBuilder -> {
              for (LocalStackContainer.Service service : services) {
                testBuilder.subConfig(
                        "upstart.aws." + service.getName().toLowerCase(),
                        conf -> configureAwsClient(service, fixture, conf)
                );
              }
              ProvisionedResource.ResourceType[] provisionedResourceTypes = Arrays.stream(services)
                      .map(service -> switch (service) {
                        case DYNAMODB -> new ProvisionedResource.ResourceType("dynamodb_table");
                        case S3 -> new ProvisionedResource.ResourceType("s3_bucket");
                        case KINESIS -> new ProvisionedResource.ResourceType("kinesis_stream");
                        case FIREHOSE -> new ProvisionedResource.ResourceType("firehose_delivery_stream");
                        default -> null;
                      }).filter(Objects::nonNull).toArray(ProvisionedResource.ResourceType[]::new);
              if (provisionedResourceTypes.length > 0) {
                testBuilder.installModule(binder -> {
                  Multibinder<ProvisionedResource.ResourceType> resourceTypeMultibinder = ProvisionedResource.provisionedResourceTypeBinder(binder);
                  for (ProvisionedResource.ResourceType resourceType : provisionedResourceTypes) {
                    resourceTypeMultibinder.addBinding().toInstance(resourceType);
                  }
                });
              }
            });
    return fixture;
  }

  private static Stream<AwsLocalStackTest> localStackAnnotations(ExtensionContext context) {
    return ExtensionContexts.testContextAnnotatedElements(context, Reflect.LineageOrder.SubclassBeforeSuperclass)
            .flatMap(instance -> Optional.ofNullable(instance.getAnnotation(AwsLocalStackTest.class)).stream());
  }

  /**
   * configures the {@link upstart.aws.AwsConfig} for the given client
   */
  private void configureAwsClient(
          LocalStackContainer.Service service,
          Fixture fixture,
          TestConfigBuilder.SubConfigBuilder clientConfig
  ) {
    clientConfig.overrideConfig("""
            region: %s
            endpoint: "%s"
            credentialsProviderType: Supplied
            credentialsProviderSupplierClass: %s
            """.formatted(fixture.container().getRegion(), fixture.endpointOverride(service), LocalStackCredentialsProvider.class.getName()));
  }

  @Override
  public Class<Fixture> contextClass() {
    return Fixture.class;
  }

  @Override
  public boolean supportsParameter(
          ParameterContext parameterContext,
          ExtensionContext extensionContext
  ) throws ParameterResolutionException {
    Class<?> type = parameterContext.getParameter().getType();
    return type.equals(LocalStackContainer.class) || SdkClient.class.isAssignableFrom(type);
  }

  @Override
  public Object resolveParameter(
          ParameterContext parameterContext,
          ExtensionContext extensionContext
  ) throws ParameterResolutionException {
    Fixture fixture = getOrCreateContext(extensionContext);
    Class<?> type = parameterContext.getParameter().getType();
    if (type.equals(LocalStackContainer.class)) {
      return fixture.container();
    } else if (SdkClient.class.isAssignableFrom(type)) {
      return fixture.client(AwsClientType.of(type.asSubclass(SdkClient.class)));
    } else {
      throw new ParameterResolutionException("Unsupported parameter type: " + type);
    }
  }

  public static final class Fixture {
    private final LocalStackContainer container;
    private final LazyReference<SdkHttpClient> httpClient = LazyReference.from(ApacheHttpClient::create);
    private final LoadingCache<AwsClientType<?, ?>, SdkClient> clientCache;

    public Fixture(String imageName, LocalStackContainer.EnabledService[] services) {
      container = new LocalStackContainer(DockerImageName.parse(imageName)).withServices(services);
      container.start();
      this.clientCache = CacheBuilder.newBuilder().build(new CacheLoader<>() {
        @Override
        public SdkClient load(AwsClientType<?, ?> clientType) throws Exception {
          LocalStackContainer.Service service = LocalStackContainer.Service.valueOf(clientType.serviceName().toUpperCase());

          AwsClientBuilder<?, ? extends SdkClient> builder = clientType.builder();
          if (builder instanceof AwsSyncClientBuilder<?, ?> syncBuilder) syncBuilder.httpClient(httpClient.get());
          return builder
                  .region(Region.of(container.getRegion()))
                  .endpointOverride(endpointOverride(service))
                  .credentialsProvider(new LocalStackCredentialsProvider())
                  .build();
        }
      });
    }

    private URI endpointOverride(LocalStackContainer.Service service) {
      return URI.create("http://localhost:" + container.getEndpointOverride(service).getPort());
    }

    public void stop() {
      MultiException.closeAll(Streams.concat(clientCache.asMap().values().stream(), Stream.of(container), httpClient.getIfPresent().stream())).throwRuntimeIfAny();
    }

    public SdkClient client(AwsClientType<?, ?> key) {
      return clientCache.getUnchecked(key);
    }

    public LocalStackContainer container() {
      return container;
    }
  }
}
