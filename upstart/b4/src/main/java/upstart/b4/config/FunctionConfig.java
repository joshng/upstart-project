package upstart.b4.config;

import com.google.common.reflect.TypeToken;
import com.google.inject.Module;
import upstart.b4.B4Function;
import upstart.b4.TargetName;
import upstart.b4.TargetSpec;
import upstart.util.Nothing;
import org.immutables.value.Value;

import java.lang.reflect.Type;
import java.util.Optional;
import java.util.function.Predicate;

@Value.Immutable
public abstract class FunctionConfig {
  public static final String IMPLEMENTATION_FIELD = "impl";
  static final Predicate<Type> CONFIGURED_TYPE = type -> type != Nothing.class; // && type != Object.class;
  private static final Type GENERIC_CONFIG_TYPE = B4Function.class.getTypeParameters()[0];

  public abstract Class<? extends B4Function<?>> impl();

  public abstract Optional<Class<? extends Module>> module();

  @Value.Derived
  @Value.Auxiliary
  public Optional<Type> configType() {
    // TODO: cache these for reused task-types?
    return Optional.of(TypeToken.of(impl()).resolveType(GENERIC_CONFIG_TYPE).getType())
            .filter(CONFIGURED_TYPE);
  }

  public TargetSpec buildSpec(TargetName name) {
    return TargetSpec.builder(name)
            .impl(impl())
            .configType(configType())
            .moduleClass(module())
            .build();
  }
}
