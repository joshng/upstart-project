package upstart.aws.test.dynamodb;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;

import java.util.function.Supplier;

public class FakeCredentialsProvider implements Supplier<AwsCredentialsProvider>, AwsCredentialsProvider {
  @Override
  public AwsCredentialsProvider get() {
    return this;
  }

  @Override
  public AwsCredentials resolveCredentials() {
    return AwsBasicCredentials.create("fake-access-key", "fake-secret");
  }
}
