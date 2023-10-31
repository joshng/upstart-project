package upstart.aws;

import com.google.inject.Binder;
import com.google.inject.Key;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Names;
import org.immutables.value.Value;
import software.amazon.awssdk.core.SdkClient;
import upstart.config.ConfigKey;
import upstart.config.UpstartConfigBinder;
import upstart.config.UpstartModule;
import upstart.guice.TypeLiterals;
import upstart.guice.UpstartPrivateModule;

import java.util.Optional;

@Value.Immutable
public abstract class AwsClientModule<C extends SdkClient> extends UpstartModule {

  public static final ConfigKey<AwsConfig.DefaultAwsConfig> DEFAULT_CONFIG_KEY = ConfigKey.of("upstart.aws.defaults", AwsConfig.DefaultAwsConfig.class);
  public static final Key<AwsConfig.DefaultAwsConfig> DEFAULT_KEY = Key.get(AwsConfig.DefaultAwsConfig.class);

  public static void installWithDefaultConfig(Binder binder, Class<? extends SdkClient> clientClass) {
    binder.install(withDefaultConfig(binder, clientClass));
  }

  public static <C extends SdkClient> AwsClientModule<C> withDefaultConfig(Binder binder, Class<C> clientClass) {
    return builder(clientClass).binderForAwsConfig(binder).build();
  }

  public static Key<AwsConfig> bindAwsConfig(Binder binder, String configPath) {
    Key<AwsConfig> key = Key.get(AwsConfig.class, Names.named(configPath));
    bindAwsConfig(binder, configPath, key);
    return key;
  }

  public static AwsConfig bindAwsConfig(Binder binder, String configPath, Key<AwsConfig> boundKey) {
    UpstartConfigBinder configBinder = UpstartConfigBinder.get();
    AwsConfig.DefaultAwsConfig defaults = configBinder.bindConfig(binder, DEFAULT_CONFIG_KEY, DEFAULT_KEY).validateDefaults();
    AwsConfig.DefaultAwsConfig overrideConfigs = configBinder.bindConfig(
            binder,
            ConfigKey.of(configPath, AwsConfig.DefaultAwsConfig.class),
            boundKey.ofType(AwsConfig.DefaultAwsConfig.class)
    );
    AwsConfig result = overrideConfigs.withDefaults(defaults);
    binder.bind(boundKey).toInstance(result);
    return result;
  }

  public abstract AwsClientType<C, ?> clientType();
  public abstract Optional<Binder> binderForAwsConfig();

  @Value.Default
  public Key<AwsConfig> awsConfigKey() {
    return bindAwsConfig(requiredBinder(), clientType().defaultConfigPath());
  }

  public Binder requiredBinder() {
    return binderForAwsConfig()
            .orElseThrow(() -> new IllegalStateException(
                    "Missing both awsConfigKey and binderForAwsConfig. Provide binderForAwsConfig to use configs from upstart.aws.<service-type>, or use bindAwsConfig()"));
  }

  @Value.Default
  public Key<AbstractAwsClientService<C, ?>> clientServiceKey() {
    AwsClientType<C, ?> clientType = clientType();
    Class<? extends AbstractAwsClientService> clientServiceType = clientType.isAsync()
            ? AwsAsyncClientService.class
            : AwsSyncClientService.class;
    TypeLiteral<AbstractAwsClientService<C,?>> serviceType = TypeLiterals.getParameterized(
            clientServiceType,
            clientType.clientClass(),
            clientType.builderClass()
    );

    return Key.get(serviceType);
  }

  public static <C extends SdkClient> Builder<C> builder(Class<C> clientClass) {
    return new Builder<C>().forClient(clientClass);
  }

  @Override
  protected void configure() {
    if (clientType().isAsync()) install(new AwsAsyncModule());

    Key<AbstractAwsClientService<C, ?>> exposedKey = clientServiceKey();
    install(new UpstartPrivateModule() {
      @Override
      protected void configure() {
        bindPrivateBinding(AwsConfig.class).to(awsConfigKey());
        if (clientType() instanceof AwsClientType.AsyncClient<?, ?> asyncClientType) {
          bindPrivateBinding(AwsClientType.AsyncClient.class).toInstance(asyncClientType);
        } else if (clientType() instanceof AwsClientType.SyncClient<?, ?> syncClientType) {
          bindPrivateBinding(AwsClientType.SyncClient.class).toInstance(syncClientType);
        }
        if (exposedKey.getAnnotationType() != null) {
          bind(exposedKey).to(exposedKey.getTypeLiteral()).asEagerSingleton();
        } else {
          bind(exposedKey).asEagerSingleton();
        }
        expose(exposedKey);
      }
    });

    serviceManager().manage(exposedKey);

    bindResourceFromProviderService(exposedKey);
  }

  @Override
  public abstract boolean equals(Object obj);

  @Override
  public abstract int hashCode();

  public static class Builder<C extends SdkClient> extends ImmutableAwsClientModule.Builder<C> {
    public Builder<C> forClient(Class<C> clientClass) {
      return clientType(AwsClientType.of(clientClass));
    }

    public Builder<C> bindAwsConfig(Binder binder, String configPath) {
      return awsConfigKey(AwsClientModule.bindAwsConfig(binder, configPath));
    }
  }
}
