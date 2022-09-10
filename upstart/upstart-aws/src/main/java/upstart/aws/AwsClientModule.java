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

@Value.Immutable
public abstract class AwsClientModule<C extends SdkClient> extends UpstartModule {

  public static void install(Binder binder, Class<? extends SdkClient> clientClass) {
    AwsServiceType<?, ?> serviceType = AwsServiceType.of(clientClass);
    Key<AwsConfig> configKey = bindAwsConfig(binder, serviceType.defaultConfigPath());
    if (serviceType.isAsync()) {
      binder.install(of((AwsServiceType.AsyncService<?, ?>) serviceType, configKey));
    } else {
      binder.install(of((AwsServiceType.SyncService<?, ?>) serviceType, configKey));
    }
  }

  public static Key<AwsConfig> bindAwsConfig(Binder binder, String configPath) {
    Key<AwsConfig> key = Key.get(AwsConfig.class, Names.named(configPath));
    bindAwsConfig(binder, configPath, key);
    return key;
  }

  public static AwsConfig bindAwsConfig(Binder binder, String configPath, Key<AwsConfig> boundKey) {
    return UpstartConfigBinder.get().bindConfig(binder,
                                                ConfigKey.of(configPath, AwsConfig.DefaultAwsConfig.class),
                                                boundKey
    );
  }

  public abstract AwsServiceType<C, ?> serviceType();
  public abstract Key<AwsConfig> awsConfigKey();
  public abstract Key<AbstractAwsClientService<C, ?>> exposedKey();

  public static <C extends SdkClient> AwsClientModule<C> of(
          Class<C> clientClass,
          Key<AwsConfig> awsConfigKey
  ) {
    return of(AwsServiceType.ofAsync(clientClass), awsConfigKey);
  }

  public static <C extends SdkClient> AwsClientModule<C> of(
          AwsServiceType<C, ?> awsServiceType,
          Key<AwsConfig> awsConfigKey
  ) {

    Class<? extends AbstractAwsClientService> clientServiceType = awsServiceType.isAsync()
            ? AwsAsyncClientService.class
            : AwsSyncClientService.class;
    TypeLiteral<AbstractAwsClientService<C,?>> serviceType = TypeLiterals.getParameterized(
            clientServiceType,
            awsServiceType.clientClass(),
            awsServiceType.builderClass()
    );

    Key<AbstractAwsClientService<C, ?>> exposedServiceKey = Key.get(serviceType);

    return new Builder<C>()
            .serviceType(awsServiceType)
            .awsConfigKey(awsConfigKey)
            .exposedKey(exposedServiceKey)
            .build();
  }

  public static <C extends SdkClient> Builder<C> builder(Class<C> clientClass) {
    return new Builder<C>().serviceType(AwsServiceType.ofAsync(clientClass));
  }

  @Override
  public abstract boolean equals(Object obj);

  @Override
  public abstract int hashCode();

  @Override
  protected void configure() {
    if (serviceType().isAsync()) install(new AwsAsyncModule());

    Key<AbstractAwsClientService<C, ?>> exposedKey = exposedKey();
    install(new UpstartPrivateModule() {
      @Override
      protected void configure() {
        bindPrivateBinding(AwsConfig.class).to(awsConfigKey());
        if (serviceType().isAsync()) {
          bindPrivateBinding(AwsServiceType.AsyncService.class).toInstance((AwsServiceType.AsyncService) serviceType());
        } else {
          bindPrivateBinding(AwsServiceType.SyncService.class).toInstance((AwsServiceType.SyncService) serviceType());
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

  public static class Builder<C extends SdkClient> extends ImmutableAwsClientModule.Builder<C> {
  }
}
