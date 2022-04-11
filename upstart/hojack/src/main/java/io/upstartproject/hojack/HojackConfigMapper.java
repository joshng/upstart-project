package io.upstartproject.hojack;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.deser.DeserializationProblemHandler;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValue;

import java.lang.reflect.Type;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

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
        Object bool = entry.getValue().unwrapped();
        if (bool instanceof Boolean) {
          String className = entry.getKey().replace("\"", "");
          if ((Boolean) bool) {
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
  public <T> T map(Object configObject, Type mappedType) {
    return objectMapper.convertValue(configObject, objectMapper.getTypeFactory().constructType(mappedType));
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
}

