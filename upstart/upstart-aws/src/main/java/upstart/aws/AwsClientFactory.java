package upstart.aws;

import software.amazon.awssdk.awscore.client.builder.AwsClientBuilder;
import upstart.guice.PrivateBinding;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class AwsClientFactory {
  private final AwsConfig config;

  @Inject
  public AwsClientFactory(@PrivateBinding AwsConfig config) {
    this.config = config;
  }

  public AwsConfig getDefaultConfig() {
    return config;
  }

  public <BuilderT extends AwsClientBuilder<BuilderT, ClientT>, ClientT> BuilderT configureClientBuilder(BuilderT builder) {
    return configureClientBuilder(builder, config);
  }

  public <BuilderT extends AwsClientBuilder<BuilderT, ClientT>, ClientT> BuilderT configureClientBuilder(BuilderT builder, AwsConfig config) {
    return builder.applyMutation(config::configure);
  }
}
