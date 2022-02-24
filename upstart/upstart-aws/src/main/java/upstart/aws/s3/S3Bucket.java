package upstart.aws.s3;

import com.fasterxml.jackson.annotation.JsonCreator;
import upstart.util.StringIdentifier;
import upstart.util.annotations.Tuple;
import org.immutables.value.Value;
import software.amazon.awssdk.regions.Region;

@Value.Immutable
@Tuple
public abstract class S3Bucket extends StringIdentifier {
  @JsonCreator
  public static S3Bucket of(Region region, String bucket) {
    return ImmutableS3Bucket.of(bucket, region);
  }

  public abstract Region region();

  public abstract S3Bucket withRegion(Region region);

  public S3Key key(S3Key.Scheme scheme, String key) {
    return S3Key.of(scheme, this, key);
  }

  @Override
  public String toString() {
    return value();
  }
}
