package upstart.config;

import com.google.inject.PrivateModule;
import com.google.inject.TypeLiteral;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import io.upstartproject.hojack.ConfigMapper;
import upstart.guice.PrivateBinding;
import upstart.guice.TypeLiterals;
import upstart.util.concurrent.CompletableFutures;
import upstart.util.functions.AsyncConsumer;

import javax.inject.Inject;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public class DynamicConfigFile<T> implements Supplier<T> {
  private final Path filePath;
  private final String configPath;
  private final Type mappedType;
  private final ConfigMapper configMapper;
  private final FileModificationWatchService service;
  private final Set<AsyncConsumer<T>> consumers = new HashSet<>();
  private final Config appConfig;
  private volatile T value;


  @Inject
  DynamicConfigFile(
          @PrivateBinding Path filePath,
          @PrivateBinding String configPath,
          TypeLiteral<T> mappedType,
          UpstartApplicationConfig applicationConfig,
          FileModificationWatchService service
  ) {
    this.filePath = filePath;
    this.configPath = configPath;
    this.mappedType = mappedType.getType();
    this.appConfig = applicationConfig.provider().resolvedBaseConfig()
            .withFallback(HojackConfigProvider.getReferenceConfig(configPath));
    this.configMapper = applicationConfig.provider().configMapper();
    this.service = service;
    service.watch(filePath, this::refresh);
  }

  @Override
  public T get() {
    return value;
  }

  public Path path() {
    return filePath;
  }

  private T reload() {
    return configMapper.mapSubConfig(reloadConfig(), configPath, this.mappedType);
  }

  public void subscribe(AsyncConsumer<T> consumer) {
    service.initialize(() -> consumers.add(consumer));
  }

  private CompletableFuture<Void> refresh(Path path) {
    T newConfig = value = reload();
    return CompletableFutures.allOf(consumers.stream().map(c -> c.apply(newConfig).toCompletableFuture()));
  }

  private Config reloadConfig() {
    Config fileConfig = Files.exists(filePath)
            ? ConfigFactory.parseFile(filePath.toFile())
            : ConfigFactory.empty();
    return fileConfig.withFallback(appConfig).resolve();
  }

  public static class DynamicConfigModule<T> extends UpstartModule {

    private final Path path;
    private final ConfigKey<T> configKey;

    public DynamicConfigModule(Path path, ConfigKey<T> configKey) {
      super(path, configKey);
      this.path = path;
      this.configKey = configKey;
    }

    @Override
    protected void configure() {
      install(new FileModificationWatchService.WatchServiceModule());

      install(new PrivateModule() {
        @Override
        protected void configure() {
          @SuppressWarnings({"unchecked"})
          TypeLiteral<DynamicConfigFile<T>> key = TypeLiterals.getParameterized(
                  DynamicConfigFile.class,
                  configKey.mappedType().getType()
          );
          bind(key).asEagerSingleton();
          bind(Path.class).annotatedWith(PrivateBinding.class).toInstance(path);
          bind(String.class).annotatedWith(PrivateBinding.class).toInstance(configKey.configPath());
          expose(key);
        }
      });
    }
  }
}
