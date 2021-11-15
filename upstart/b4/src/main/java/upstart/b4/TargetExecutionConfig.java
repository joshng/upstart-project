package upstart.b4;

import org.immutables.value.Value;

import java.util.Optional;

public interface TargetExecutionConfig {
  TargetExecutionConfig DEFAULT = new TargetExecutionConfig() {
    @Override
    public Optional<TargetInvocation.Phases> phases() {
      return Optional.empty();
    }

    @Override
    public Optional<B4Function.Verbosity> verbosity() {
      return Optional.empty();
    }
  };

  static TargetExecutionConfig copyOf(TargetExecutionConfig instance) {
    if (instance instanceof ImmutableExtractedExecutionConfig) return instance;
    return ExtractedExecutionConfig.builder().from(instance).build();
  }

  static TargetExecutionConfig merge(TargetExecutionConfig parent, TargetExecutionConfig child) {
    return ExtractedExecutionConfig.builder().from(parent).from(child).build();
  }

  @Value.Auxiliary
  Optional<TargetInvocation.Phases> phases();

  @Value.Auxiliary
  Optional<B4Function.Verbosity> verbosity();

  default B4Function.Verbosity effectiveVerbosity() {
    return verbosity().orElse(B4Function.Verbosity.Info);
  }

  default TargetInvocation.Phases effectivePhases() {
    return phases().orElse(TargetInvocation.Phases.DirtyRun);
  }

  default boolean doClean() {
    return effectivePhases().doClean;
  }

  default boolean doRun() {
    return effectivePhases().doRun;
  }
  @Value.Immutable
  interface ExtractedExecutionConfig extends TargetExecutionConfig {
    static ImmutableExtractedExecutionConfig.Builder builder() {
      return ImmutableExtractedExecutionConfig.builder();
    }


  }
}
