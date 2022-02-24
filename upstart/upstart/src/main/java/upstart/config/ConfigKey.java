package upstart.config;

import com.google.inject.TypeLiteral;
import upstart.config.annotations.ConfigPath;
import upstart.util.annotations.Tuple;
import com.typesafe.config.ConfigUtil;
import org.immutables.value.Value;

import java.util.List;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * Identifies a configuration-object that can be loaded from application config data.
 *
 * @param <T> the type of object that will hold the mapped configuration values
 * @see #of
 */
@Value.Immutable
@Tuple
public interface ConfigKey<T> {
  /**
   * Computes the {@link ConfigKey} for the given class, obtaining its {@link #configPath} from the {@link ConfigPath}
   * annotation which is required to be defined on the <code>mappedClass</code>.
   * @param mappedClass the class to hold the mapped config-values
   * @throws IllegalArgumentException if the provided <code>mappedClass</code> is missing a {@link ConfigPath} annotation
   */
  static <T> ConfigKey<T> of(Class<T> mappedClass) {
    return of(requiredConfigPath(mappedClass), mappedClass);
  }

  /**
   * Constructs a {@Link ConfigKey} for the given <code>configPath</code> and <code>mappedClass</code>
   * @see #of(Class)
   */
  static <T> ConfigKey<T> of(String configPath, Class<T> mappedClass) {
    return of(configPath, TypeLiteral.get(mappedClass));
  }

  /**
   * Constructs a {@Link ConfigKey} for the given <code>configPath</code> and <code>mappedType</code>.
   * Note that this method is only necessary if the <code>mappedType</code> includes generic type-variables; otherwise,
   * {@link #of(Class)} is more convenient.
   *
   * @see #of(Class)
   */
  static <T> ConfigKey<T> of(String configPath, TypeLiteral<T> mappedType) {
    return ImmutableConfigKey.of(configPath, mappedType);
  }

  static String requiredConfigPath(Class<?> configClass) {
    ConfigPath pathAnnotation = configClass.getAnnotation(ConfigPath.class);
    checkArgument(pathAnnotation != null, "Config classes must be annotated with @%s: ", ConfigPath.class.getSimpleName(), configClass);
    return pathAnnotation.value();
  }

  /**
   * The path within the application configuration containing data to be loaded into an instance of the {@link #mappedType}. Usually
   * obtained from a {@link ConfigPath} annotation on the class indicated by the {@link #mappedType}.
   */
  String configPath();

  /**
   * The java-type to be populated with application configuration data from the {@link #configPath}
   */
  TypeLiteral<T> mappedType();

  @SuppressWarnings("unchecked")
  default T cast(Object object) {
    return (T) mappedType().getRawType().cast(object);
  }

  default String contentPathPrefix() {
    return configPath() + '.';
  }

  @Value.Derived
  @Value.Auxiliary
  default List<String> pathSegments() {
    return ConfigUtil.splitPath(configPath());
  }
}
