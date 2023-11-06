package upstart.aws.s3;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import upstart.util.annotations.Tuple;
import org.immutables.value.Value;

import java.io.Serial;
import java.io.Serializable;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.google.common.base.Preconditions.checkArgument;

@Value.Immutable
@Tuple
public abstract class S3Key implements Serializable {
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

  @Value.Lazy
  public S3Key parentDirectory() {
    return withKey(key().substring(0, key().lastIndexOf('/')));
  }

  @Value.Lazy
  public String filename() {
    return key().substring(key().lastIndexOf('/') + 1);
  }

  public abstract S3Key withBucket(S3Bucket bucket);

  public abstract S3Key withScheme(Scheme scheme);

  public abstract S3Key withKey(String key);

  public S3Key withFilename(String filename) {
    return parentDirectory().resolve(filename);
  }

  public S3Key resolve(String relativePath) {
    return withKey(appendWithSlash(new StringBuilder(key()), relativePath).toString());
  }

  public S3Key resolve(String... tokens) {
    StringBuilder keyBuilder = new StringBuilder(key());
    for (String token : tokens) {
      appendWithSlash(keyBuilder, token);
    }
    return withKey(keyBuilder.toString());
  }

  public boolean contains(S3Key childKey) {
    return key().endsWith("/") && childKey.key().startsWith(key()) || childKey.key().startsWith(key() + "/");
  }

  public Consumer<PutObjectRequest.Builder> putObjectRequestBuilder() {
    return b -> b.bucket(bucket().value()).key(key());
  }

  @Value.Check
  void checkValidValue() {
    checkArgument(VALID_KEY_IDENTIFIER.matcher(key()).matches(), "Invalid key, must match %s: %s", VALID_KEY_IDENTIFIER, key());
  }

  @Override
  public String toString() {
    return uri();
  }

  private static StringBuilder appendWithSlash(StringBuilder sb, String s) {
    if (!s.isEmpty() && !s.startsWith("/") && !sb.isEmpty() && sb.charAt(sb.length() - 1) != '/') sb.append('/');
    return sb.append(s);
  }

  public enum Scheme {
    s3,
    s3a
  }

  @Serial
  Object writeReplace() {
    return new Serialized(uri());
  }


  private record Serialized(String uri) implements Serializable {
    @Serial
    Object readResolve() {
      return S3Key.ofUri(uri);
    }
  }
}
