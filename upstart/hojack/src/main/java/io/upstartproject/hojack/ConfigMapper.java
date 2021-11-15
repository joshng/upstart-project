package io.upstartproject.hojack;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigValue;

import java.lang.reflect.Type;

public interface ConfigMapper {
  <T> T map(Object configObject, Type mappedType);

  ConfigMapper copy();

  Config asConfig(String path, Object mappedValue, String originDescription);

  default <T> T mapConfig(Config config, Type mappedType) {
    return mapConfigValue(config.root(), mappedType);
  }

  default <T> T mapConfig(Config config, Class<T> mappedType) {
    return mapConfigValue(config.root(), mappedType);
  }

  default <T> T mapSubConfig(Config config, String path, Class<T> mappedType) {
    return mappedType.cast(mapSubConfig(config, path, (Type)mappedType));
  }

  default <T> T mapSubConfig(Config config, String path, Type mappedType) {
    return mapConfigValue(config.getValue(path), mappedType);
  }

  default <T> T mapConfigValue(ConfigValue configValue, Type mappedType) {
    return map(configValue.unwrapped(), mappedType);
  }

  default <T> T mapConfigValue(ConfigValue configValue, Class<T> mappedType) {
    return map(configValue.unwrapped(), mappedType);
  }
}
