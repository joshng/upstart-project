package upstart.aws;

import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.awscore.client.builder.AwsClientBuilder;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class AwsClientFactory {
  private final AwsCredentialsProvider credentialsProvider;
  private final AwsModule.AwsConfig config;

  @Inject
  public AwsClientFactory(AwsCredentialsProvider credentialsProvider, AwsModule.AwsConfig config) {
    this.credentialsProvider = credentialsProvider;
    this.config = config;
  }

  public AwsModule.AwsConfig getDefaultConfig() {
    return config;
  }

  public <BuilderT extends AwsClientBuilder<BuilderT, ClientT>, ClientT> BuilderT configureClientBuilder(BuilderT builder) {
    return configureClientBuilder(builder, config);
  }

  public <BuilderT extends AwsClientBuilder<BuilderT, ClientT>, ClientT> BuilderT configureClientBuilder(BuilderT builder, AwsModule.AwsConfig config) {
    return builder.credentialsProvider(credentialsProvider)
            .applyMutation(config::configure);
  }
}
