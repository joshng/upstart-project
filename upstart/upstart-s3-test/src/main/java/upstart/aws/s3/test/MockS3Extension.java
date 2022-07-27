package upstart.aws.s3.test;

import upstart.test.ExtensionContexts;
import upstart.test.UpstartExtension;
import upstart.test.SingletonParameterResolver;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

import static com.google.common.base.Predicates.not;

public class MockS3Extension extends SingletonParameterResolver<MockS3> implements BeforeEachCallback, AfterEachCallback {
  protected MockS3Extension() {
    super(MockS3.class);
  }

  @Override
  protected MockS3 createContext(ExtensionContext extensionContext) throws Exception {
    Optional<MockS3Test> anno = ExtensionContexts.findNearestAnnotation(MockS3Test.class, extensionContext);
    int port = anno.map(MockS3Test::port).orElse(MockS3Test.DEFAULT_PORT);
    Optional<Path> fileDirectory = anno.map(MockS3Test::fileDirectory).filter(not(String::isEmpty)).map(Paths::get);
    String[] initialBuckets = anno.map(MockS3Test::initialBuckets).orElse(new String[0]);

    MockS3 mockS3 = new MockS3(port, initialBuckets, fileDirectory);
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
}
