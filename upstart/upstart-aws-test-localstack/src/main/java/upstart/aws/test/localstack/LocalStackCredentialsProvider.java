package upstart.aws.test.localstack;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;

import java.util.function.Supplier;

public class LocalStackCredentialsProvider implements Supplier<AwsCredentialsProvider>, AwsCredentialsProvider {
  @Override
  public AwsCredentialsProvider get() {
    return this;
  }

  @Override
  public AwsCredentials resolveCredentials() {
    return AwsBasicCredentials.create("accessKey", "secretKey");
  }
}
