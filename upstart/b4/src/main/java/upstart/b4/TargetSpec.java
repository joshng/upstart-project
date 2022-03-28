package upstart.b4;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Streams;
import com.google.inject.Module;
import com.google.inject.util.Modules;
import upstart.b4.config.TargetConfigurator;
import upstart.util.collect.Optionals;
import upstart.util.reflect.Reflect;
import org.immutables.value.Value;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static com.google.common.base.Preconditions.checkArgument;

// TODO: make this polymorphic: either task or target
@Value.Immutable
@Value.Style(deepImmutablesDetection = true)
public abstract class TargetSpec {
  public static ImmutableTargetSpec.Builder builder(TargetName name) {
    return ImmutableTargetSpec.builder().name(name);
  }

  public abstract TargetName name();

  public abstract Optional<String> description();

  public abstract List<TargetConfigurator> taskConfigs();

  public abstract List<TargetConfigurator> dependencies();

  public abstract List<TargetConfigurator> tasks();

  public Stream<TargetConfigurator> subTargetConfigurators() {
    return Streams.concat(taskConfigs().stream(), dependencies().stream(), tasks().stream());
  }

  public abstract Optional<Class<? extends B4Function<?>>> impl();

  public abstract Optional<Module> module();

  @Value.Default
  public Optional<Type> configType() {
    return Optional.empty();
  }

  private static List<TargetConfigurator> mergeConfigurators(List<TargetConfigurator> a, List<TargetConfigurator> b) {
    return TargetConfigurator.mergeConfigurators(Stream.concat(a.stream(), b.stream())).collect(ImmutableList.toImmutableList());
  }

  public TargetSpec merge(TargetSpec other) {
    Optional<Class<? extends B4Function<?>>> mergedImpl = Optionals.merge(impl(), other.impl(), (a, b) -> {
      checkArgument(a.equals(b), "Mismatched target implementations: %s", name(), a, b);
      return a;
    });
    return builder(other.name())
            .description(Optionals.merge(description(), other.description(), (a, b) -> a + "\n" + b))
            .taskConfigs(mergeConfigurators(taskConfigs(), other.taskConfigs()))
            .dependencies(mergeConfigurators(dependencies(), other.dependencies()))
            .tasks(mergeConfigurators(tasks(), other.tasks()))
            .impl(mergedImpl)
            .module(module().map(m1 -> other.module().map(m2 -> Modules.combine(m1, m2)).or(this::module)).orElse(other.module()))
            .build();
  }

  public static abstract class Builder {
    public abstract ImmutableTargetSpec.Builder module(Optional<? extends Module> module);

    public abstract ImmutableTargetSpec.Builder addDependencies(TargetConfigurator element);

    public ImmutableTargetSpec.Builder addDependency(TargetSpec target) {
      return addDependency(target.name());
    }

    public ImmutableTargetSpec.Builder addDependency(TargetName target) {
      return addDependencies(target.emptyConfigurator());
    }

//    public ImmutableTargetSpec.Builder globalConfigOverrides(String config) {
//      return globalConfigOverrides(ConfigFactory.parseString(config,
//              ConfigParseOptions.defaults().setOriginDescription("TaskGenerator"))
//      );
//    }

    public ImmutableTargetSpec.Builder moduleClass(Class<? extends Module> moduleClass) {
      return moduleClass(Optional.ofNullable(moduleClass));
    }

    public ImmutableTargetSpec.Builder moduleClass(Optional<Class<? extends Module>> moduleClass) {
      return module(moduleClass.map(Reflect::newInstance));
    }
  }
}
