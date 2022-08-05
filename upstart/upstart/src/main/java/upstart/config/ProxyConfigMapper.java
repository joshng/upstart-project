package upstart.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.google.common.base.Predicates;
import com.google.common.base.Throwables;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.primitives.Primitives;
import com.google.common.reflect.AbstractInvocationHandler;
import com.google.common.reflect.TypeToken;
import io.upstartproject.hojack.Size;
import upstart.config.annotations.ConfigPath;
import upstart.proxy.Proxies;
import upstart.util.reflect.Modifiers;
import upstart.util.collect.MoreCollectors;
import upstart.util.collect.Optionals;
import upstart.util.collect.PairStream;
import upstart.util.reflect.Reflect;
import upstart.util.exceptions.ThrowingFunction;
import upstart.util.annotations.Tuple;
import upstart.util.concurrent.CompletableFutures;
import upstart.util.concurrent.Promise;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigMemorySize;
import com.typesafe.config.ConfigObject;
import com.typesafe.config.ConfigValue;
import org.immutables.value.Value;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

@SuppressWarnings("Guava") // there's nothing wrong with Predicates.alwaysTrue()
public class ProxyConfigMapper {
  private static final Map<Type, TypeInspector> TYPE_INSPECTORS = new ConcurrentHashMap<>();
  private static final LoadingCache<Class<?>, CompletableFuture<ConfigObjectProxyMapper>> PROXY_MAPPERS = CacheBuilder.newBuilder()
          .build(CacheLoader.from(ConfigObjectProxyMapper::forClass));
  private static final LoadingCache<Method, CompletableFuture<MethodMapper>> METHOD_MAPPERS = CacheBuilder.newBuilder()
          .build(CacheLoader.from(MethodMapper::forMethod));
  private static final Set<Type> JSON_CONVERTIBLE_TYPES = ImmutableSet.<Type>builder().addAll(Primitives.allWrapperTypes())
          .add(String.class)
          .add(Duration.class)
          .add(ConfigMemorySize.class)
          .add(Size.class)
          .build();

  private static final Type MAP_KEY_TYPE;
  private static final Type MAP_VALUE_TYPE;
  private static final Type COLLECTION_VALUE_TYPE;
  private static final Type OPTIONAL_VALUE_TYPE;
  private static final Map<Class<?>, TypeMapperFactory> TYPE_MAPPER_FACTORIES = new ConcurrentHashMap<>();

  static {
    Type[] mapParamTypes = Map.class.getTypeParameters();
    MAP_KEY_TYPE = mapParamTypes[0];
    MAP_VALUE_TYPE = mapParamTypes[1];
    COLLECTION_VALUE_TYPE = Collection.class.getTypeParameters()[0];
    OPTIONAL_VALUE_TYPE = Optional.class.getTypeParameters()[0];
    TYPE_MAPPER_FACTORIES.put(List.class, TypeMapperFactory.LIST);
    TYPE_MAPPER_FACTORIES.put(ImmutableList.class, TypeMapperFactory.LIST);
    TYPE_MAPPER_FACTORIES.put(Set.class, TypeMapperFactory.SET);
    TYPE_MAPPER_FACTORIES.put(ImmutableSet.class, TypeMapperFactory.SET);
    TYPE_MAPPER_FACTORIES.put(Optional.class, TypeMapperFactory.OPTIONAL);
    TYPE_MAPPER_FACTORIES.put(Map.class, TypeMapperFactory.MAP);
    TYPE_MAPPER_FACTORIES.put(ImmutableMap.class, TypeMapperFactory.MAP);
    TYPE_MAPPER_FACTORIES.put(Class.class, TypeMapperFactory.CLASS);
  }

  private final ObjectMapper objectMapper;

  public ProxyConfigMapper(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  /** use this to add custom support for deserializing instances of rawMappedType from HOCON */
  public static void registerGenericTypeMapperFactory(Class<?> rawMappedType, TypeMapperFactory factory) {
    TYPE_MAPPER_FACTORIES.put(rawMappedType, factory);
  }

  public <T> T map(Config config, Class<T> mappedType) {
    return map(config, mappedType, Predicates.alwaysTrue());
  }

  public <T> T map(Config config, Class<T> mappedType, Predicate<String> mappedKeyFilter) {
    return mappedType.cast(proxyMapper(mappedType).join().map(config, mappedKeyFilter, objectMapper));
  }

  private static CompletableFuture<TypeMapper> typeMapper(Type type) {
    return TYPE_INSPECTORS.computeIfAbsent(type, TypeInspector::new).getMapper();
  }

  private static CompletableFuture<MethodMapper> methodMapper(Method method) {
    return METHOD_MAPPERS.getUnchecked(method);
  }

  private static <A, R> CompletableFuture<TypeMapper> collectionTypeMapper(TypeToken<?> collectionTypeToken, Collector<Object, A, R> collector) {
    Type memberType = collectionTypeToken.resolveType(COLLECTION_VALUE_TYPE).getType();
    // TODO: support recursion
    checkArgument(memberType instanceof Class, "Cannot map type: %s", collectionTypeToken);
    Class<?> memberClass = (Class<?>) memberType;

    if (JSON_CONVERTIBLE_TYPES.contains(memberType) || isJsonCreatable(memberClass)) {
      JavaType jsonType = TypeFactory.defaultInstance().constructType(collectionTypeToken.getType());
      return CompletableFuture.completedFuture((config, path, objectMapper) ->
              objectMapper.convertValue(config.hasPath(path) ? config.getList(path).unwrapped() : ImmutableList.of(), jsonType));
    } else if (memberType == Config.class) {
      return CompletableFuture.completedFuture(TypeMapper.collection(Config::getConfigList, collector));
    } else if (memberType == ConfigObject.class) {
      return CompletableFuture.completedFuture(TypeMapper.collection(Config::getObjectList, collector));
    } else if (memberType == ConfigValue.class) {
      return CompletableFuture.completedFuture(TypeMapper.collection(Config::getList, collector));
    } else {
      // assume we can proxy this type
      return proxyMapper(memberClass)
              .thenApply(objMapper -> ((config, path, objectMapper) -> config.hasPath(path)
                      ? config.getConfigList(path).stream().map(c -> objMapper.map(c, Predicates.alwaysTrue(), objectMapper)).collect(collector)
                      : collector.finisher().apply(collector.supplier().get())));
    }
  }

  private static boolean isJsonCreatable(Class<?> type) {
    return Stream.concat(Arrays.stream(type.getDeclaredMethods()), Arrays.stream(type.getDeclaredConstructors()))
            .anyMatch(m -> m.isAnnotationPresent(JsonCreator.class));
  }

  private static CompletableFuture<TypeMapper> buildProxyMapper(Class<?> mappedType) {
    return proxyMapper(mappedType).thenApply(TypeMapper::subObjectMapper);
  }

  private static CompletableFuture<ConfigObjectProxyMapper> proxyMapper(Class<?> mappedType) {
    return PROXY_MAPPERS.getUnchecked(mappedType);
  }

  private static boolean isMappedMethod(Method method) {
    if (Modifiers.Concrete.matches(method)) return false;
    if (method.getParameterCount() > 0) {
      checkArgument(method.isDefault(), "Cannot proxy method with parameters: %s", method);
      return false;
    }
    return true;
  }

  @Value.Immutable
  @Tuple
  static abstract class MethodMapper {
    abstract Method method();
    abstract String configPath();
    abstract TypeMapper typeMapper();

    static CompletableFuture<MethodMapper> forMethod(Method method) {
      Reflect.setAccessible(method);
      CompletableFuture<TypeMapper> typeMapper = ProxyConfigMapper.typeMapper(method.getGenericReturnType());
      Optional<String> adustedPath = Optional.ofNullable(method.getAnnotation(ConfigPath.class))
              .map(ConfigPath::value)
              .or(() -> Optional.ofNullable(method.getAnnotation(JsonProperty.class)).map(JsonProperty::value));
      String path = adustedPath.orElse(method.getName());
      return typeMapper.thenApply(mapper -> ImmutableMethodMapper.of(method, path, mapper));
    }

    Object mapValue(Config config, ObjectMapper objectMapper) {
      return typeMapper().mapValue(config, configPath(), objectMapper);
    }
  }

  interface TypeMapper {
    Object mapValue(Config config, String path, ObjectMapper objectMapper);

    static TypeMapper of(BiFunction<Config, String, ?> withoutObjectMapper) {
      return (config, path, om) -> withoutObjectMapper.apply(config, path);
    }

    static TypeMapper collection(BiFunction<Config, String, ? extends Collection<?>> listExtractor, Collector<Object, ?, ?> collector) {
      return (config, path, om) -> listExtractor.apply(config, path).stream().collect(collector);
    }

    static TypeMapper subObjectMapper(ConfigObjectProxyMapper subObjectMapper) {
      return (config, path, om) -> subObjectMapper.map(config.getConfig(path), Predicates.alwaysTrue(), om);
    }
  }

  static class TypeInspector {
    private final Type type;
    private final Promise<TypeMapper> promise = new Promise<>();
    private final AtomicBoolean loaded = new AtomicBoolean();

    TypeInspector(Type type) {
      this.type = type;
    }

    CompletableFuture<TypeMapper> getMapper() {
      if (loaded.compareAndSet(false, true)) promise.completeWith(buildMapper());
      return promise;
    }

    private CompletableFuture<TypeMapper> buildMapper() {
      return Optionals.asInstance(type, Class.class)
                  .map(returnType -> PrimitiveConfigExtractor.getExtractor(returnType)
                          .map(extractor -> CompletableFuture.completedFuture(TypeMapper.of(extractor::extractValue)))
                          .orElseGet(() -> buildProxyMapper(returnType)))
                  .orElseGet(() -> {
                    // handle a generic type; we'll only support maps, lists, and sets for now?
                    return buildGenericTypeMapper(type);
                  });
    }

  }

  public static CompletableFuture<TypeMapper> buildGenericTypeMapper(Type type) {
    TypeToken<?> token = TypeToken.of(type);
    // TODO: could support subtypes of map/list/set by passing through objectMapper.convertValue?
    return checkNotNull(TYPE_MAPPER_FACTORIES.get(token.getRawType()), "No TypeMapperFactory registered for type: %s", token)
            .buildTypeMapper(token);
  }

  private static CompletableFuture<TypeMapper> mapMapper(TypeToken<?> collectionTypeToken) {
    Type keyType = collectionTypeToken.resolveType(MAP_KEY_TYPE).getType();
    BiFunction<String, ObjectMapper, ?> keyMapper = keyType.equals(String.class)
            ? (key, o) -> key
            : (key, objectMapper) -> objectMapper.convertValue(key, TypeFactory.defaultInstance()
                    .constructType(keyType));

    Type valueType = collectionTypeToken.resolveType(MAP_VALUE_TYPE).getType();
    return typeMapper(valueType)
            .thenApply(valueMapper -> (config, path, objectMapper) -> {
              if (config.hasPath(path)) {
                Config mapConfig = config.getConfig(path);
                return PairStream.withMappedValues(
                        mapConfig.root().keySet().stream(),
                        key -> valueMapper.mapValue(mapConfig, key, objectMapper)
                ).mapKeys(key -> keyMapper.apply(key, objectMapper)
                ).toImmutableMap();
              } else {
                return ImmutableMap.of();
              }
            });
  }

  private static class ConfigObjectProxyMapper {
    private final Class<?> mappedType;
    private final Map<Method, MethodMapper> methodMappers;
    private final Set<String> validKeys;
    private final Function<Object, Object> postProc;

    private ConfigObjectProxyMapper(Class<?> mappedType, List<MethodMapper> methodMappers) {
      this.mappedType = mappedType;
      this.methodMappers = methodMappers.stream().collect(MoreCollectors.toImmutableIndexMap(MethodMapper::method));
      this.validKeys = methodMappers.stream().map(MethodMapper::configPath).collect(Collectors.toSet());
      postProc = Arrays.stream(this.mappedType.getDeclaredMethods())
              .filter(method -> Modifiers.Static.matches(method) && method.getName().equals("builder") && method.getParameterCount() == 0)
              .findFirst()
              .flatMap(ProxyConfigMapper::copyConstructor)
              .orElse(Function.identity());
    }

    static CompletableFuture<ConfigObjectProxyMapper> forClass(Class<?> mappedType) {
      Set<String> uniqueNames = new HashSet<>();
      Stream<CompletableFuture<MethodMapper>> methodMappers = Reflect.allDeclaredMethods(mappedType, Reflect.LineageOrder.SubclassBeforeSuperclass)
              .filter(ProxyConfigMapper::isMappedMethod)
              .filter(m -> uniqueNames.add(m.getName()))
              .map(ProxyConfigMapper::methodMapper);
      return CompletableFutures.allAsList(methodMappers)
              .thenApply(mappers -> new ConfigObjectProxyMapper(mappedType, mappers));
    }

    Object map(Config config, Predicate<String> mappedKeyFilter, ObjectMapper objectMapper) {
      List<String> invalidKeys = config.root().keySet().stream()
              .filter(mappedKeyFilter.and(key -> !validKeys.contains(key)))
              .collect(Collectors.toList());

      checkArgument(invalidKeys.isEmpty(), "Cannot map config into instance of %s, unexpected keys: %s\n%s",
              mappedType.getName(),
              invalidKeys,
              config.root()
      );
      Map<Method, Object> values = PairStream.of(methodMappers)
                      .mapValues((method, mapper) -> mapper.mapValue(config, objectMapper))
                      .toImmutableMap();

      DefaultMethodInvoker methodHandleLookup = new DefaultMethodInvoker(ConfigObjectProxyMapper.this.mappedType);

      return postProc.apply(Proxies.createProxy(mappedType, new AbstractInvocationHandler() {
        @Override
        protected Object handleInvocation(Object proxy, Method method, Object[] args) throws Throwable {
          // TODO what about toString, hashCode, etc
          Object result = values.get(method);
          if (result == null && !values.containsKey(method)) {
            result = methodHandleLookup.invokeDefaultMethod(proxy, method, args);
          }
          return result;
        }
      }));
    }

    /**
     * this is awful: invoking interface-defined `default` methods from a java proxy is pretty awkward; see:
     * https://blog.jooq.org/2018/03/28/correct-reflective-access-to-interface-default-methods-in-java-8-9-10/
     */
    public static class DefaultMethodInvoker {
      private static final MethodHandles.Lookup METHODHANDLES_LOOKUP = MethodHandles.lookup();
      private final Class<?> mappedType;
      private final MethodHandles.Lookup privateLookup;

      public DefaultMethodInvoker(Class<?> mappedType) {
        this.mappedType = mappedType;
        try {
          privateLookup = MethodHandles.privateLookupIn(mappedType, METHODHANDLES_LOOKUP);
        } catch (IllegalAccessException e) {
          throw new RuntimeException(e);
        }
      }

      public Object invokeDefaultMethod(Object proxy, Method method, Object... args) throws Throwable {
        return privateLookup.unreflectSpecial(method, mappedType)
                .bindTo(proxy)
                .invokeWithArguments(args);
      }
    }
  }

  private static Optional<Function<Object, Object>> copyConstructor(Method builderMethod) {
    Method fromMethod;
    Method buildMethod;
    try {
      Reflect.setAccessible(builderMethod);
      Class<?> builderClass = builderMethod.getReturnType();
      fromMethod = builderClass.getMethod("from", builderMethod.getDeclaringClass());
      Reflect.setAccessible(fromMethod);
      buildMethod = builderClass.getMethod("build");
      Reflect.setAccessible(buildMethod);
    } catch (NoSuchMethodException e) {
      return Optional.empty();
    }
    return Optional.of(ThrowingFunction.uncheckedFunction(proxy -> {
      try {
        return buildMethod.invoke(fromMethod.invoke(builderMethod.invoke(null), proxy));
      } catch (InvocationTargetException e) {
        // unwrap to protect type-specific catch-logic up the stack
        Throwables.propagateIfPossible(e.getTargetException(), Exception.class);
        throw e;
      }
    }));
  }

  public interface TypeMapperFactory {
    TypeMapperFactory LIST = typeToken -> collectionTypeMapper(typeToken, ImmutableList.toImmutableList());
    TypeMapperFactory SET = typeToken -> collectionTypeMapper(typeToken, ImmutableSet.toImmutableSet());
    TypeMapperFactory OPTIONAL = ProxyConfigMapper::optionalMapper;
    TypeMapperFactory MAP = ProxyConfigMapper::mapMapper;
    TypeMapperFactory CLASS = typeToken -> CompletableFuture.completedFuture(classNameMapper(typeToken));

    CompletableFuture<TypeMapper> buildTypeMapper(TypeToken<?> mappedType);
  }

  private static TypeMapper classNameMapper(TypeToken<?> typeToken) {
    Class<?> baseType = TypeToken.of(Reflect.<Type>getFirstGenericType(typeToken.getType())).getRawType();
    return (config, path, objectMapper) -> Reflect.classForName(config.getString(path), baseType);
  }

  private static CompletableFuture<TypeMapper> optionalMapper(TypeToken<?> typeToken) {
    return typeMapper(typeToken.resolveType(OPTIONAL_VALUE_TYPE).getType())
            .thenApply(mapper -> ((config, path, om) -> Optionals
            .onlyIfFrom(config.hasPath(path), () -> mapper.mapValue(config, path, om))));
  }
}
