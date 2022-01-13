package upstart.b4;

import com.google.inject.Key;
import com.google.inject.PrivateModule;
import com.google.inject.Scopes;
import com.google.inject.multibindings.MapBinder;
import com.google.inject.name.Names;
import upstart.config.UpstartModule;
import upstart.guice.PrivateBinding;
import upstart.util.Nothing;
import upstart.util.Optionals;

public class B4TargetModule extends UpstartModule {
  private final TargetSpec spec;
  private final TargetInvocation invocation;

  public B4TargetModule(TargetInvocation invocation) {
    this.spec = invocation.spec();
    this.invocation = invocation;
  }

  @Override
  protected void configure() {
    Key<TargetRunner> targetKey = invocation.runnerKey();
    Key<?> configKey = spec.configType().<Key<?>>map(type -> {
      Key<?> key = Key.get(type, Names.named(invocation.configPath()));
      bindConfig(invocation.configPath(), key);
      return key;
    }).orElse(Key.get(Nothing.class));

    // TODO: specialize everything for tasks vs. targets?
    install(new PrivateModule() {
      @Override
      protected void configure() {
        bind(B4Function.class).to(spec.impl().orElse(NoopFunction.class));
        bind(Object.class).annotatedWith(PrivateBinding.class).to(configKey);
        bind(TargetInvocation.class).toInstance(invocation);
        bind(targetKey).to(TargetRunner.class).in(Scopes.SINGLETON);
        expose(targetKey);
      }
    });

    MapBinder.newMapBinder(binder(), TargetInstanceId.class, TargetRunner.class).addBinding(invocation.id()).to(targetKey);
    spec.module().ifPresent(this::install);
  }

  @Override
  public boolean equals(Object o) {
    return this == o || Optionals.asInstance(o, B4TargetModule.class)
            .filter(that -> invocation.equals(that.invocation))
            .isPresent();
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + invocation.hashCode();
    return result;
  }

  @Override
  public String toString() {
    return "B4TargetModule{" +
            "invocation=" + invocation +
            '}';
  }
}
