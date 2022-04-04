package upstart.aws.s3;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import upstart.util.annotations.Tuple;
import org.immutables.value.Value;
import software.amazon.awssdk.regions.Region;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.google.common.base.Preconditions.checkArgument;

@Value.Immutable
@Tuple
public abstract class S3Key {
  public static final Pattern VALID_KEY_IDENTIFIER = Pattern.compile("[a-zA-Z0-9@_./-]+");
  // TODO: this is a placeholder structure, may change when we implement real s3 integration
  public static final Pattern S3_URI_PATTERN = Pattern.compile("^(s3a?)://([^/]+)/(.*)$");

  public static S3Key of(S3Bucket bucket, String key) {
    return of(Scheme.s3, bucket, key);
  }

  public static S3Key of(Scheme scheme, S3Bucket bucket, String key) {
    return ImmutableS3Key.of(scheme, bucket, key);
  }

  @JsonCreator
  public static S3Key ofUri(String uri) {
    Matcher matcher = S3_URI_PATTERN.matcher(uri);
    checkArgument(matcher.matches(), "Invalid s3:// uri", uri);
    return of(Scheme.valueOf(matcher.group(1)), S3Bucket.of(matcher.group(2)), matcher.group(3));
  }

  public abstract Scheme scheme();

  public abstract S3Bucket bucket();

  public abstract String key();

  @JsonValue
  @Value.Lazy
  public String uri() {
    return String.format("%s://%s/%s", scheme(), bucket(), key());
  }

  public abstract S3Key withScheme(Scheme scheme);

  public abstract S3Key withKey(String key);

  public S3Key resolve(String relativePath) {
    StringBuilder keyBuilder = new StringBuilder(key());
    if (!key().endsWith("/")) keyBuilder.append('/');
    keyBuilder.append(relativePath);
    return withKey(keyBuilder.toString());
  }

  public S3Key resolve(String... tokens) {
    StringBuilder keyBuilder = new StringBuilder(key());
    boolean needSlash = !key().endsWith("/");
    for (String token : tokens) {
      if (needSlash) keyBuilder.append('/');
      keyBuilder.append(token);
      needSlash = !token.endsWith("/");
    }
    return withKey(keyBuilder.toString());
  }

  @Value.Check
  void checkValidValue() {
    checkArgument(VALID_KEY_IDENTIFIER.matcher(key()).matches(), "Invalid key, must match %s: %s", VALID_KEY_IDENTIFIER, key());
  }

  @Override
  public String toString() {
    return uri();
  }

  public enum Scheme {
    s3,
    s3a
  }
}
