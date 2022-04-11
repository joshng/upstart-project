package upstart.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.upstartproject.hojack.HojackConfigMapper;
import upstart.json.ImmutablesModule;
import upstart.util.Ambiance;
import upstart.util.reflect.Reflect;

public interface ObjectMapperFactory {
  static ObjectMapper buildAmbientObjectMapper() {
    return Ambiance.ambientValue("upstart.objectMapperFactory")
            .map(factoryClass -> Reflect.newInstance(factoryClass, ObjectMapperFactory.class))
            .orElse(HojackObjectMapperFactory.Instance)
            .buildObjectMapper();
  }

  ObjectMapper buildObjectMapper();

  enum HojackObjectMapperFactory implements ObjectMapperFactory {
    Instance;

    @Override
    public ObjectMapper buildObjectMapper() {
      return HojackConfigMapper.buildDefaultObjectMapper()
              .registerModule(new ImmutablesModule());
    }
  }
}
