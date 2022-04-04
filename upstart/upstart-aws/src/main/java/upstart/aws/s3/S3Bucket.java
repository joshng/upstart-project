package upstart.aws.s3;

import com.fasterxml.jackson.annotation.JsonCreator;
import upstart.util.annotations.Identifier;
import upstart.util.strings.StringIdentifier;
import upstart.util.annotations.Tuple;
import org.immutables.value.Value;
import software.amazon.awssdk.regions.Region;

@Identifier
public abstract class S3Bucket extends StringIdentifier {
  @JsonCreator
  public static S3Bucket of(String bucket) {
    return ImmutableS3Bucket.of(bucket);
  }

  public S3Key key(String key) {
    return key(S3Key.Scheme.s3, key);
  }

  public S3Key key(S3Key.Scheme scheme, String key) {
    return S3Key.of(scheme, this, key);
  }

  @Override
  public String toString() {
    return value();
  }
}
