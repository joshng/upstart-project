package upstart.aws.s3.test;

import com.google.common.io.Resources;
import org.apache.http.entity.ContentType;
import org.immutables.value.Value;
import upstart.aws.s3.S3Bucket;
import upstart.aws.s3.S3Key;
import upstart.util.exceptions.UncheckedIO;

import java.nio.charset.StandardCharsets;

@Value.Immutable
public interface S3Fixture {
  static Builder builder(String bucket, String key) {
    return builder(S3Bucket.of(bucket), key);
  }

  static Builder builder(S3Bucket bucket, String key) {
    return builder().key(S3Key.of(bucket, key));
  }

  static Builder builder() {
    return new Builder();
  }

  static S3Fixture fromResource(S3Key key, String resourcePath) {
    return builder().key(key).dataFromResource(resourcePath).build();
  }

  S3Key key();

  byte[] data();

  default ContentType contentType() {
    return ContentType.DEFAULT_BINARY;
  }

  class Builder extends ImmutableS3Fixture.Builder {
    public Builder data(String data) {
      return data(data.getBytes(StandardCharsets.UTF_8));
    }

    public Builder dataFromResource(String resourcePath) {
      return data(UncheckedIO.getUnchecked(() -> Resources.toByteArray(Resources.getResource(resourcePath))));
    }
  }
}
