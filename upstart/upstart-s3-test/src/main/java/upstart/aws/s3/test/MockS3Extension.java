package upstart.aws.s3.test;

import upstart.aws.s3.S3Key;
import upstart.test.AvailablePortAllocator;
import upstart.test.ExtensionContexts;
import upstart.test.UpstartExtension;
import upstart.test.SingletonParameterResolver;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.google.common.base.Predicates.not;

public class MockS3Extension extends SingletonParameterResolver<MockS3> implements BeforeEachCallback, AfterEachCallback {

  protected MockS3Extension() {
    super(MockS3.class);
  }

  @Override
  protected MockS3 createContext(ExtensionContext extensionContext) throws Exception {
    // TODO: support multiple instances of @MockS3Test annotation for composable fixtures
    Optional<MockS3Test> anno = ExtensionContexts.findNearestAnnotation(MockS3Test.class, extensionContext);
    int port = anno.map(MockS3Test::port).filter(p -> p > 0).orElseGet(AvailablePortAllocator::allocatePort);
    Optional<Path> fileDirectory = anno.map(MockS3Test::fileDirectory).filter(not(String::isEmpty)).map(Paths::get);
    String[] initialBuckets = anno.map(MockS3Test::initialBuckets).orElse(new String[0]);
    List<S3Fixture> fixtures = anno.map(MockS3Test::value)
            .stream()
            .flatMap(Arrays::stream)
            .map(MockS3Extension::toResourceFixture)
            .toList();

    Set<String> buckets = Stream.concat(
            Arrays.stream(initialBuckets),
            fixtures.stream().map(fixture -> fixture.key().bucket().value())
    ).collect(Collectors.toSet());

    MockS3 mockS3 = new MockS3(port, buckets, fileDirectory);
    fixtures.forEach(mockS3::putFixture);

    System.setProperty("fs.s3a.endpoint", mockS3.getEndpointUri().toString());
    System.setProperty("fs.s3a.aws.credentials.provider", "org.apache.hadoop.fs.s3a.AnonymousAWSCredentialsProvider");

    UpstartExtension.applyOptionalEnvironmentValues(extensionContext, mockS3);

    return mockS3;
  }

  @Override
  public void beforeEach(ExtensionContext extensionContext) {
    getOrCreateContext(extensionContext);
  }

  @Override
  public void afterEach(ExtensionContext extensionContext) {
    getExistingContext(extensionContext).ifPresent(MockS3::shutdown);
  }

  public static S3Fixture toResourceFixture(MockS3Test.Fixture anno) {
    String uri = anno.uri();
    if (!uri.startsWith("s3://")) uri = "s3://" + uri;
    return S3Fixture.fromResource(S3Key.ofUri(uri), anno.fromResource());
  }
}
