package io.upstartproject.hojack;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.typesafe.config.ConfigMemorySize;

import java.io.IOException;

public class MemorySizeConfigDeserializer extends JsonDeserializer<ConfigMemorySize> {
  public static final Module JACKSON_MODULE = new SimpleModule()
          .addDeserializer(ConfigMemorySize.class, new MemorySizeConfigDeserializer());

  @Override
  public ConfigMemorySize deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
      return ConfigMemorySize.ofBytes(Size.parse(p.getValueAsString()).toBytes());
  }
}
