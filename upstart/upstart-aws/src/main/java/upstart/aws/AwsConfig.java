package upstart.aws;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.base.Throwables;
import org.immutables.value.Value;
import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.awscore.client.builder.AwsClientBuilder;
import software.amazon.awssdk.core.retry.RetryPolicy;
import software.amazon.awssdk.regions.Region;
import upstart.config.annotations.DeserializedImmutable;

import java.net.URI;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

public interface AwsConfig {
  Optional<URI> endpoint();

  Optional<String> region();

  CredentialsProviderType credentialsProviderType();

  Optional<Class<? extends Supplier<AwsCredentialsProvider>>> credentialsProviderSupplierClass();

  AwsConfig withEndpoint(URI endpoint);

  AwsConfig withRegion(String region);

  int maxRetries();

  default AwsConfig withRegion(Region region) {
    return withRegion(region.id());
  }

  @JsonIgnore
  @Value.Derived
  @Value.Auxiliary
  default Optional<Region> sdkRegion() {
    return region().map(Region::of);
  }

  default <BuilderT extends AwsClientBuilder<BuilderT, ClientT>, ClientT> BuilderT configure(BuilderT builder) {
    endpoint().ifPresent(builder::endpointOverride);
    sdkRegion().ifPresent(builder::region);
    return builder
            .credentialsProvider(credentialsProvider())
            .overrideConfiguration(b -> b.retryPolicy(
                    RetryPolicy.defaultRetryPolicy().copy(rp -> rp.numRetries(maxRetries())))
            );
  }

  default void applyS3AProperties(BiConsumer<String, String> configuration) {
    endpoint().map(URI::toString).or(() -> sdkRegion().map(region -> String.format(
            "https://s3.%s.amazonaws.com",
            region
    ))).ifPresent(endpoint -> configuration.accept("fs.s3a.endpoint", endpoint));
    if (credentialsProviderType() == CredentialsProviderType.Anonymous) {
      configuration.accept(
              "fs.s3a.aws.credentials.provider",
              "org.apache.hadoop.fs.s3a.AnonymousAWSCredentialsProvider"
      );
    }
  }

  @JsonIgnore
  @Value.Lazy
  default AwsCredentialsProvider credentialsProvider() {
    return credentialsProviderType().getProvider(this);
  }

  enum CredentialsProviderType {
    Default {
      @Override
      AwsCredentialsProvider getProvider(AwsConfig config) {
        return DefaultCredentialsProvider.create();
      }
    }, Anonymous {
      @Override
      AwsCredentialsProvider getProvider(AwsConfig config) {
        return AnonymousCredentialsProvider.create();
      }
    }, Supplied {
      @Override
      AwsCredentialsProvider getProvider(AwsConfig config) {
        try {
          return config.credentialsProviderSupplierClass().orElseThrow().getConstructor().newInstance().get();
        } catch (Exception e) {
          Throwables.throwIfUnchecked(e);
          throw new RuntimeException(e);
        }
      }
    };

    abstract AwsCredentialsProvider getProvider(AwsConfig config);
  }

  @DeserializedImmutable
  interface DefaultAwsConfig extends AwsConfig {
  }
}