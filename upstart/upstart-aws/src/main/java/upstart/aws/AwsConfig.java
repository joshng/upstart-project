package upstart.aws;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.base.Throwables;
import org.immutables.value.Value;
import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.WebIdentityTokenFileCredentialsProvider;
import software.amazon.awssdk.awscore.client.builder.AwsClientBuilder;
import software.amazon.awssdk.core.retry.RetryMode;
import software.amazon.awssdk.core.retry.RetryPolicy;
import software.amazon.awssdk.regions.Region;
import upstart.config.annotations.DeserializedImmutable;

import java.net.URI;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

public interface AwsConfig {
  Optional<URI> endpoint();

  Optional<String> region();

  Optional<CredentialsProviderType> credentialsProviderType();

  Optional<Class<? extends Supplier<AwsCredentialsProvider>>> credentialsProviderSupplierClass();

  Optional<RetryMode> retryMode();

  OptionalInt maxRetries();

  AwsConfig withEndpoint(URI endpoint);

  AwsConfig withRegion(String region);

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
                                           RetryPolicy.builder(retryMode().orElseThrow())
                                                   .applyMutation(rb -> maxRetries().ifPresent(rb::numRetries))
                                                   .build()
                                   )
            );
  }

  default void applyS3AProperties(BiConsumer<String, String> configuration) {
    endpoint().map(URI::toString).or(() -> sdkRegion().map(region -> String.format(
            "https://s3.%s.amazonaws.com",
            region
    ))).ifPresent(endpoint -> configuration.accept("fs.s3a.endpoint", endpoint));
    if (credentialsProviderType().orElseThrow() == CredentialsProviderType.Anonymous) {
      configuration.accept(
              "fs.s3a.aws.credentials.provider",
              "org.apache.hadoop.fs.s3a.AnonymousAWSCredentialsProvider"
      );
    }
  }

  @JsonIgnore
  @Value.Lazy
  default AwsCredentialsProvider credentialsProvider() {
    return credentialsProviderType().orElseThrow().getProvider(this);
  }

  enum CredentialsProviderType {
    Default {
      @Override
      AwsCredentialsProvider getProvider(AwsConfig config) {
        return DefaultCredentialsProvider.create();
      }
    },
    Anonymous {
      @Override
      AwsCredentialsProvider getProvider(AwsConfig config) {
        return AnonymousCredentialsProvider.create();
      }
    },
    WebIdentityTokenFile {
      @Override
      AwsCredentialsProvider getProvider(AwsConfig config) {
        return WebIdentityTokenFileCredentialsProvider.builder()
                .roleArn(System.getenv("AWS_ROLE_ARN"))
                .webIdentityTokenFile(Paths.get(System.getenv("AWS_WEB_IDENTITY_TOKEN_FILE")))
                .build();
      }
    },
    Supplied {
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
    static ImmutableDefaultAwsConfig.Builder builder() {
      return ImmutableDefaultAwsConfig.builder();
    }
    default AwsConfig withDefaults(AwsConfig defaults) {
      return builder()
              .from(defaults)
              .from(this)
              .build();
    }

    default DefaultAwsConfig validateDefaults() {
      List<String> missingFields = new ArrayList<>();
      if (credentialsProviderType().isEmpty()) missingFields.add("credentialsProviderType");
      if (retryMode().isEmpty()) missingFields.add("retryMode");
      if (!missingFields.isEmpty()) {
        throw new IllegalStateException("Missing required fields: " + missingFields);
      }
      return this;
    }
  }
}
