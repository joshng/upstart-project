package upstart.json;

import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.DeserializationConfig;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.introspect.BasicBeanDescription;
import com.fasterxml.jackson.databind.introspect.BasicClassIntrospector;
import com.fasterxml.jackson.databind.introspect.ClassIntrospector;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import org.kohsuke.MetaInfServices;
import upstart.config.annotations.AlternativeImmutableAnnotation;
import upstart.config.annotations.DeserializedImmutable;
import upstart.util.reflect.Reflect;

import java.util.Optional;

@MetaInfServices(Module.class)
public class ImmutablesModule extends Module {
  @Override
  public String getModuleName() {
    return "Immutables";
  }

  @Override
  public Version version() {
    return Version.unknownVersion();
  }

  @Override
  public void setupModule(SetupContext context) {
    context.setClassIntrospector(ImmutableAwareClassIntrospector.INSTANCE);
  }

  private static class ImmutableAwareClassIntrospector extends BasicClassIntrospector {
    static final ImmutableAwareClassIntrospector INSTANCE = new ImmutableAwareClassIntrospector();
    private static final LoadingCache<Class<?>, Optional<JavaType>> IMMUTABLE_TYPES = CacheBuilder.newBuilder()
            .build(new CacheLoader<>() {
              @Override
              public Optional<JavaType> load(Class<?> requested) throws Exception {
                return Reflect.allMetaAnnotations(requested)
                        .filter(anno -> anno.annotationType().isAnnotationPresent(AlternativeImmutableAnnotation.class))
                        .findFirst()
                        .flatMap(anno -> anno instanceof DeserializedImmutable di
                                && di.deserializeAs() != Void.class
                                ? Optional.of(TypeFactory.defaultInstance().constructType(di.deserializeAs()))
                                : guessImmutableType(requested)
                        );
              }

              private static Optional<JavaType> guessImmutableType(Class<?> requested) {
                // TODO: try to reverse-engineer Value.Style behavior?
                String name = requested.getPackageName() + ".Immutable" + requested.getSimpleName();
                Class<?> immutableClass;
                try {
                  immutableClass = Class.forName(name);
                } catch (ClassNotFoundException e) {
                  return Optional.empty();
                }
                return Optional.of(TypeFactory.defaultInstance().constructType(immutableClass));
              }
            });


    @Override
    public BasicBeanDescription forDeserialization(
            DeserializationConfig config, JavaType type, MixInResolver r
    ) {
      return super.forDeserialization(config, resolveImmutableType(type), r);
    }

    @Override
    public BasicBeanDescription forCreation(
            DeserializationConfig config, JavaType type, MixInResolver r
    ) {
      return super.forCreation(config, resolveImmutableType(type), r);
    }

    @Override
    public BasicBeanDescription forSerialization(
            SerializationConfig config, JavaType type, MixInResolver r
    ) {
      return super.forSerialization(config, resolveImmutableType(type), r);
    }

    private static JavaType resolveImmutableType(JavaType type) {
      return IMMUTABLE_TYPES.getUnchecked(type.getRawClass()).orElse(type);
    }

    @Override
    public ClassIntrospector copy() {
      return this;
    }
  }
}
