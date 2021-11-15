package upstart.config;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.DeserializationProblemHandler;
import com.fasterxml.jackson.databind.deser.ValueInstantiator;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import io.upstartproject.hojack.HojackConfigMapper;
import org.immutables.value.Value;
import upstart.config.annotations.DeserializedImmutable;
import upstart.util.Ambiance;
import upstart.util.Reflect;

import java.io.IOException;
import java.util.Optional;
import java.util.regex.Pattern;

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
              .addHandler(new ImmutableDeserializationHandler());
    }

    private static class ImmutableDeserializationHandler extends DeserializationProblemHandler {
      private static final Pattern IMMUTABLE_NAME_PATTERN = Pattern.compile("\\*");
      private static final LoadingCache<Class<?>, Optional<Class<?>>> IMMUTABLE_TYPES = CacheBuilder.newBuilder()
              .build(CacheLoader.from(ImmutableDeserializationHandler::immutableClass));

      @Override
      public Object handleMissingInstantiator(DeserializationContext ctxt, Class<?> instClass, ValueInstantiator valueInsta, JsonParser p, String msg) throws IOException {
        Optional<Class<?>> immutable = IMMUTABLE_TYPES.getUnchecked(instClass);
        if (immutable.isPresent()) {
          return p.readValueAs(immutable.get());
        } else {
          return NOT_HANDLED;
        }
      }

      private static Optional<Class<?>> immutableClass(Class<?> requested) {
        return Reflect.findMetaAnnotations(DeserializedImmutable.class, requested)
                .<Class<?>>map(DeserializedImmutable::deserializeAs)
                .filter(type -> type != Void.class)
                .findFirst()
                .or(() -> {
                  String classname = requested.getPackageName() + ".Immutable" + requested.getSimpleName();
                  try {
                    return Optional.of(Class.forName(classname));
                  } catch (ClassNotFoundException e) {
                    return Optional.empty();
                  }
                });
      }
    }
  }
}
