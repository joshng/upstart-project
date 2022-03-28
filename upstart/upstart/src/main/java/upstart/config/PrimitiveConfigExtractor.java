package upstart.config;

import com.google.common.collect.ImmutableMap;
import upstart.util.collect.Optionals;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigList;
import com.typesafe.config.ConfigMemorySize;
import com.typesafe.config.ConfigObject;
import com.typesafe.config.ConfigValue;

import java.lang.reflect.Type;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

public enum PrimitiveConfigExtractor {

  BOOLEAN(boolean.class, Boolean.class) {
    @Override
    public Object extractValue(Config config, String path) {
      return config.getBoolean(path);
    }
  },
  BYTE(byte.class, Byte.class) {
    @Override
    public Object extractValue(Config config, String path) {
      return (byte) config.getInt(path);
    }
  },
  SHORT(short.class, Short.class) {
    @Override
    public Object extractValue(Config config, String path) {
      return (short) config.getInt(path);
    }
  },
  INTEGER(int.class, Integer.class) {
    @Override
    public Object extractValue(Config config, String path) {
      return config.getInt(path);
    }
  },
  LONG(long.class, Long.class) {
    @Override
    public Object extractValue(Config config, String path) {
      return config.getLong(path);
    }
  },
  FLOAT(float.class, Float.class) {
    @Override
    public Object extractValue(Config config, String path) {
      return (float) config.getDouble(path);
    }
  },
  DOUBLE(double.class, Double.class) {
    @Override
    public Object extractValue(Config config, String path) {
      return config.getDouble(path);
    }
  },
  STRING(String.class) {
    @Override
    public Object extractValue(Config config, String path) {
      return config.getString(path);
    }
  },
  ANY_REF(Object.class) {
    @Override
    public Object extractValue(Config config, String path) {
      return config.getAnyRef(path);
    }
  },
  CONFIG(Config.class) {
    @Override
    public Object extractValue(Config config, String path) {
      return config.getConfig(path);
    }
  },
  CONFIG_OBJECT(ConfigObject.class) {
    @Override
    public Object extractValue(Config config, String path) {
      return config.getObject(path);
    }
  },
  CONFIG_VALUE(ConfigValue.class) {
    @Override
    public Object extractValue(Config config, String path) {
      return config.getValue(path);
    }
  },
  CONFIG_LIST(ConfigList.class) {
    @Override
    public Object extractValue(Config config, String path) {
      return config.getList(path);
    }
  },
  DURATION(Duration.class) {
    @Override
    public Object extractValue(Config config, String path) {
      return config.getDuration(path);
    }
  },
  MEMORY_SIZE(ConfigMemorySize.class) {
    @Override
    public Object extractValue(Config config, String path) {
      return config.getMemorySize(path);
    }
  };

  public abstract Object extractValue(Config config, String path);

  private final Class<?>[] matchingClasses;
  private static final Map<Class<?>, PrimitiveConfigExtractor> EXTRACTOR_MAP;

  static {
    ImmutableMap.Builder<Class<?>, PrimitiveConfigExtractor> mapBuilder = ImmutableMap.builder();
    for (PrimitiveConfigExtractor extractor : PrimitiveConfigExtractor.values()) {
      for (Class<?> clazz : extractor.getMatchingClasses()) {
        mapBuilder.put(clazz, extractor);
      }
    }
    EXTRACTOR_MAP = mapBuilder.build();
  }

  PrimitiveConfigExtractor(Class<?>... matchingClasses) {
    this.matchingClasses = matchingClasses;
  }

  public Class<?>[] getMatchingClasses() {
    return matchingClasses;
  }

  public static Optional<Object> extractConfigValue(Config config, Type paramType, String path) {
    return getExtractor(paramType)
            .map(extractor -> extractor.extractValue(config, path));
  }

  public static Optional<PrimitiveConfigExtractor> getExtractor(Type paramType) {
    return Optionals.asInstance(paramType, Class.class)
            .map(EXTRACTOR_MAP::get);
  }

  public static boolean isExtractableType(Class<?> type) {
    return EXTRACTOR_MAP.containsKey(type);
  }
}
