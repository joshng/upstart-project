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

public interface AwsClientType<C extends SdkClient, B extends AwsClientBuilder<B, C>> {

  @SuppressWarnings("unchecked")
  static <C extends SdkClient, B extends AwsClientBuilder<B, C>> AwsClientType<C, B> of(Class<C> clientClass) {
    return clientClass.getSimpleName().endsWith("AsyncClient")
            ? (AwsClientType<C, B>) ofAsync(clientClass)
            : (SyncClient<C, B>) AwsClientTypeCache.SYNC_CACHE.getUnchecked(clientClass);
  }

  @SuppressWarnings("unchecked")
  static <C extends SdkClient, B extends AwsClientBuilder<B, C> & AwsAsyncClientBuilder<B, C>> AsyncClient<C, B> ofAsync(Class<C> clientClass) {
    return (AsyncClient<C, B>) AwsClientTypeCache.ASYNC_CACHE.getUnchecked(clientClass);
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
    return this instanceof AwsClientType.AsyncClient<?, ?>;
  }

  @SuppressWarnings("unchecked")
  default B builder() {
    return Unchecked.getUnchecked(() -> (B) builderMethod().invoke(null));
  }

  @Tuple
  interface SyncClient<C extends SdkClient, B extends AwsClientBuilder<B, C>> extends AwsClientType<C, B> {
  }

  @Tuple
  interface AsyncClient<C extends SdkClient, B extends AwsClientBuilder<B, C> & AwsAsyncClientBuilder<B, C>> extends AwsClientType<C, B> {
  }
}

class AwsClientTypeCache {
  static final LoadingCache<Class<? extends SdkClient>, AwsClientType.SyncClient<?, ?>> SYNC_CACHE = CacheBuilder.newBuilder()
          .build(new CacheLoader<>() {
            @Override
            public AwsClientType.SyncClient<?, ?> load(Class<? extends SdkClient> key) throws Exception {
              return createSync(key);
            }
          });

  static final LoadingCache<Class<? extends SdkClient>, AwsClientType.AsyncClient<?, ?>> ASYNC_CACHE = CacheBuilder.newBuilder()
          .build(new CacheLoader<>() {
            @Override
            public AwsClientType.AsyncClient<?, ?> load(Class<? extends SdkClient> key) throws Exception {
              return createAsync(key);
            }
          });

  private static <C extends SdkClient, B extends AwsClientBuilder<B, C>> AwsClientType.SyncClient<C, B> createSync(Class<C> clientClass) throws NoSuchMethodException {
    Method builderMethod = clientClass.getMethod("builder");
    String className = clientClass.getSimpleName();
    checkArgument(className.endsWith("Client"), "Client class name must match '<aws-service>[Async]Client'");
    Class<B> builderClass = (Class<B>) builderMethod.getReturnType();

    String serviceName = className.substring(0, className.length() - "Client".length()).toLowerCase();
    return ImmutableSyncClient.of(serviceName, clientClass, builderClass, builderMethod);
  }

  private static <C extends SdkClient, B extends AwsClientBuilder<B, C> & AwsAsyncClientBuilder<B, C>> AwsClientType.AsyncClient<C, B> createAsync(Class<C> clientClass) throws NoSuchMethodException {
    Method builderMethod = clientClass.getMethod("builder");
    String className = clientClass.getSimpleName();
    checkArgument(className.endsWith("AsyncClient"), "Client class name must match '<aws-service>[Async]Client'");
    Class<B> builderClass = (Class<B>) builderMethod.getReturnType();

    String serviceName = className.substring(0, className.length() - "AsyncClient".length()).toLowerCase();
    return ImmutableAsyncClient.of(serviceName, clientClass, builderClass, builderMethod);
  }
}

