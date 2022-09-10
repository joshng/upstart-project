package upstart.aws;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import org.immutables.value.Value;
import software.amazon.awssdk.awscore.client.builder.AwsAsyncClientBuilder;
import software.amazon.awssdk.awscore.client.builder.AwsClientBuilder;
import software.amazon.awssdk.core.SdkClient;
import upstart.util.annotations.Tuple;
import upstart.util.exceptions.Unchecked;

import java.lang.reflect.Method;

import static com.google.common.base.Preconditions.checkArgument;

public interface AwsServiceType<C extends SdkClient, B extends AwsClientBuilder<B, C>> {

  @SuppressWarnings("unchecked")
  static <C extends SdkClient, B extends AwsClientBuilder<B, C>> AwsServiceType<C, B> of(Class<C> clientClass) {
    return clientClass.getSimpleName().endsWith("AsyncClient")
            ? (AwsServiceType<C, B>) ofAsync(clientClass)
            : (SyncService<C, B>) AwsServiceTypeCache.SYNC_CACHE.getUnchecked(clientClass);
  }

  @SuppressWarnings("unchecked")
  static <C extends SdkClient, B extends AwsClientBuilder<B, C> & AwsAsyncClientBuilder<B, C>> AsyncService<C, B> ofAsync(Class<C> clientClass) {
    return (AsyncService<C, B>) AwsServiceTypeCache.ASYNC_CACHE.getUnchecked(clientClass);
  }

  String serviceName();
  Class<C> clientClass();
  Class<B> builderClass();

  Method builderMethod();

  @Value.Derived
  @Value.Auxiliary
  default String defaultConfigPath() {
    return "upstart.aws." + serviceName();
  }

  default boolean isAsync() {
    return this instanceof AwsServiceType.AsyncService<?, ?>;
  }

  @SuppressWarnings("unchecked")
  default B builder() {
    return Unchecked.getUnchecked(() -> (B) builderMethod().invoke(null));
  }

  @Tuple
  interface SyncService<C extends SdkClient, B extends AwsClientBuilder<B, C>> extends AwsServiceType<C, B> {
  }

  @Tuple
  interface AsyncService<C extends SdkClient, B extends AwsClientBuilder<B, C> & AwsAsyncClientBuilder<B, C>> extends AwsServiceType<C, B> {
  }
}

class AwsServiceTypeCache  {
  static final LoadingCache<Class<? extends SdkClient>, AwsServiceType.SyncService<?, ?>> SYNC_CACHE = CacheBuilder.newBuilder()
          .build(new CacheLoader<>() {
            @Override
            public AwsServiceType.SyncService<?, ?> load(Class<? extends SdkClient> key) throws Exception {
              return createSync(key);
            }
          });

  static final LoadingCache<Class<? extends SdkClient>, AwsServiceType.AsyncService<?, ?>> ASYNC_CACHE = CacheBuilder.newBuilder()
          .build(new CacheLoader<>() {
            @Override
            public AwsServiceType.AsyncService<?, ?> load(Class<? extends SdkClient> key) throws Exception {
              return createAsync(key);
            }
          });

  private static <C extends SdkClient, B extends AwsClientBuilder<B, C>> AwsServiceType.SyncService<C, B> createSync(Class<C> clientClass) throws NoSuchMethodException {
    Method builderMethod = clientClass.getMethod("builder");
    String className = clientClass.getSimpleName();
    checkArgument(className.endsWith("Client"), "Client class name must match '<aws-service>[Async]Client'");
    Class<B> builderClass = (Class<B>) builderMethod.getReturnType();

    String serviceName = className.substring(0, className.length() - "Client".length()).toLowerCase();
    return ImmutableSyncService.of(serviceName, clientClass, builderClass, builderMethod);
  }

  private static <C extends SdkClient, B extends AwsClientBuilder<B, C> & AwsAsyncClientBuilder<B, C>> AwsServiceType.AsyncService<C, B> createAsync(Class<C> clientClass) throws NoSuchMethodException {
    Method builderMethod = clientClass.getMethod("builder");
    String className = clientClass.getSimpleName();
    checkArgument(className.endsWith("AsyncClient"), "Client class name must match '<aws-service>[Async]Client'");
    Class<B> builderClass = (Class<B>) builderMethod.getReturnType();

    String serviceName = className.substring(0, className.length() - "AsyncClient".length()).toLowerCase();
    return ImmutableAsyncService.of(serviceName, clientClass, builderClass, builderMethod);
  }
}

