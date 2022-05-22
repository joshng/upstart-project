package io.upstartproject.hojack;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValue;

import java.lang.reflect.Type;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * Provides conversion of HOCON-encoded {@link Config} values to and from types supported by a Jackson {@link ObjectMapper}.
 * <p/>
 * By default, includes support for loading human-friendly time ('5s') and size ('32Mb') values into instances of
 * {@link Duration} and {@link Size} (or {@link com.typesafe.config.ConfigMemorySize}).
 * <p/>
 * Works especially well with <code>@Value.Immutable</code> types generated by http://immutables.github.io/, and
 * annotated with {@link JsonDeserialize @JsonDeserialize(as = ImmutableTypeName.class)}.
 * <p/>
 * For example, given this snippet of HOCON:
 * <pre>
 *  my.config {
 *    intValue: 7
 *    timeout: 500ms
 *  }
 * </pre>
 *
 * ... load it into Java:
 * <pre>{@code
 * @Value.Immutable
 * @JsonDeserialize(as = ImmutableMyConfig.class)
 * public interface MyConfig {
 *   int intValue();
 *   Duration timeout();
 * }
 *
 * static MyConfig loadMyConfig(Config fullConfig) {
 *   HojackConfigMapper mapper = new HojackConfigMapper(); // note that reusing mappers is recommended!
 *   return mapper.mapSubConfig(fullConfig, "my.config", MyConfig.class);
 * }
 * }</pre>
 *
 * @see #registerModule
 * @see #getObjectMapper
 * @see DurationConfigDeserializer
 * @see MemorySizeConfigDeserializer
 */
public class HojackConfigMapper implements ConfigMapper {
  private static final Logger LOG = Logger.getLogger(HojackConfigMapper.class.getName());
  public static final TypeReference<Map<String, Object>> JSON_MAP_TYPE = new TypeReference<>() { };
  public static final String HOJACK_REGISTERED_MODULES_CONFIGPATH = "hojack.registeredModules";
  private static final Predicate<String> NUMERIC_KEY_PREDICATE = Pattern.compile("\\d+").asMatchPredicate();

  private volatile static List<Module> _registeredModules = null;

  private final ObjectMapper objectMapper;

  public static ObjectMapper buildDefaultObjectMapper() {
    return buildDefaultObjectMapper(findRegisteredModules());
  }

  public static ObjectMapper buildDefaultObjectMapper(Iterable<Module> modules) {
    return new ObjectMapper()
            .registerModules(modules)
            // ensure that our simplified config-parsers are registered last
            .registerModule(DurationConfigDeserializer.JACKSON_MODULE)
            .registerModule(MemorySizeConfigDeserializer.JACKSON_MODULE)
            .disable(SerializationFeature.WRITE_DATE_TIMESTAMPS_AS_NANOSECONDS)
            .disable(JsonGenerator.Feature.AUTO_CLOSE_TARGET)
            .disable(DeserializationFeature.READ_DATE_TIMESTAMPS_AS_NANOSECONDS)
            ;
  }

  public static List<Module> findRegisteredModules() {
    if (_registeredModules == null) {
      synchronized (HojackConfigMapper.class) {
        if (_registeredModules == null) _registeredModules = computeRegisteredModules();
      }
    }
    return _registeredModules;
  }

  private static List<Module> computeRegisteredModules() {
    List<Module> defaultModules = new ArrayList<>(ObjectMapper.findModules());
    Config moduleConfig;
    try {
      moduleConfig = ConfigFactory.defaultReference().getConfig(HOJACK_REGISTERED_MODULES_CONFIGPATH);
    } catch (Exception e) {
      LOG.warning("Error loading HOCON config: \n" + e.getClass() + ": " + e.getMessage());
      throw e;
    }
    if (!moduleConfig.isEmpty()) {
      Set<String> excludedModuleClasses = new HashSet<>();
      for (Map.Entry<String, ConfigValue> entry : moduleConfig.entrySet()) {
        if (entry.getValue().unwrapped() instanceof Boolean register) {
          String className = entry.getKey().replace("\"", "");
          if (register) {
            try {
              defaultModules.add(Class.forName(className).asSubclass(Module.class).newInstance());
            } catch (Exception e) {
              throw new RuntimeException("Unable to register requested hojack.registeredModule: " + className, e);
            }
          } else {
            excludedModuleClasses.add(className);
          }
        } else {
          throw new IllegalArgumentException("Values under '" + HOJACK_REGISTERED_MODULES_CONFIGPATH + "' config must be booleans; found " + entry);
        }
      }

      if (!excludedModuleClasses.isEmpty()) {
        defaultModules.removeIf(module -> excludedModuleClasses.contains(module.getClass().getName()));
      }
    }
    return defaultModules;
  }

  public HojackConfigMapper() {
    this(buildDefaultObjectMapper());
  }

  public HojackConfigMapper(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public HojackConfigMapper registerModule(Module module) {
    objectMapper.registerModule(module);
    return this;
  }

  public ObjectMapper getObjectMapper() {
    return objectMapper;
  }

  @Override
  public HojackConfigMapper copy() {
    return new HojackConfigMapper(objectMapper.copy());
  }

  @Override
  public Config asConfig(String path, Object mappedValue, String originDescription) {
    Map<String, Object> nativeMap = Collections.singletonMap(path, mappedValue);
    Map<String, Object> primitiveMap = objectMapper.convertValue(nativeMap, JSON_MAP_TYPE);
    return ConfigFactory.parseMap(primitiveMap, originDescription);
  }

  @SuppressWarnings("unchecked")
  @Override
  public <T> T map(Object configObject, Type mappedType) {
    JavaType javaType = objectMapper.getTypeFactory().constructType(mappedType);
    while (true) {
      try {
        return objectMapper.convertValue(configObject, javaType);
      } catch (IllegalArgumentException e) {
        if (e.getCause() instanceof MismatchedInputException mismatch
                && Collection.class.isAssignableFrom(mismatch.getTargetType())
                && configObject instanceof Map map
        ) {
          convertNumberedMapToList((Map<String, Object>) map, mismatch.getPath(), e);
        } else {
          throw e;
        }
      }
    }
  }

  /**
   * HOCON supports populating a list of values by defining config-objects with numeric field-names
   * (eg, <code>{some.config.list.0: "first value", some.config.list.1: "second value"}</code><p/>
   *
   * This method achieves the same behavior when converting unwrapped JSON objects ({@link Map}), by
   * replacing a conforming Map in the unwrapped value with the corresponding {@link List}.
   */
  private static void convertNumberedMapToList(
          Map<String,Object> configObject,
          List<JsonMappingException.Reference> path,
          IllegalArgumentException origException
  ) {
    Supplier<IllegalArgumentException> exceptionSupplier = () -> origException;
    // dig out the parent-object containing the field with the mismatched datatype
    int pathPrefixDepth = path.size() - 1;
    Map<String,Object> parent = path.stream()
            .limit(pathPrefixDepth)
            .reduce(
                    configObject,
                    (map, ref) -> subMap(map, ref.getFieldName()).orElseThrow(exceptionSupplier),
                    (a, b) -> { throw new AssertionError(); }
            );

    // if the problem-field is a Map containing numeric keys, build the list of its values;
    // otherwise, rethrow the original exception (from exceptionSupplier)
    String problemFieldName = path.get(pathPrefixDepth).getFieldName();
    Map<String, Object> numberedMap = subMap(parent, problemFieldName)
            .filter(subMap -> subMap.keySet().stream().allMatch(NUMERIC_KEY_PREDICATE))
            .orElseThrow(exceptionSupplier);

    List<Object> asList = numberedMap.entrySet().stream()
            .map(OrdinalMapEntry::new)
            .sorted()
            .map(OrdinalMapEntry::value)
            .toList();

    // replace the map value with the inferred list
    parent.put(problemFieldName, asList);
  }

  @SuppressWarnings("unchecked")
  private static Optional<Map<String, Object>> subMap(Map<String, Object> container, String fieldName) {
    return container.get(fieldName) instanceof Map subMap ? Optional.of(subMap) : Optional.empty();
  }

  private record OrdinalMapEntry(int key, Object value) implements Comparable<OrdinalMapEntry> {
    public OrdinalMapEntry(Map.Entry<String, Object> entry) {
      this(Integer.parseInt(entry.getKey()), entry.getValue());
    }

    @Override
    public int compareTo(OrdinalMapEntry o) {
      return Integer.compare(key, o.key);
    }
  }
}