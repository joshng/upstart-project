package upstart.guice;

import com.google.common.base.Joiner;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.Sets;
import com.google.common.collect.Streams;
import com.google.inject.Binding;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Scopes;
import com.google.inject.assistedinject.AssistedInjectBinding;
import com.google.inject.assistedinject.AssistedInjectTargetVisitor;
import com.google.inject.multibindings.MapBinderBinding;
import com.google.inject.multibindings.MultibinderBinding;
import com.google.inject.multibindings.MultibindingsTargetVisitor;
import com.google.inject.multibindings.OptionalBinderBinding;
import com.google.inject.spi.ConstructorBinding;
import com.google.inject.spi.ConvertedConstantBinding;
import com.google.inject.spi.DefaultBindingTargetVisitor;
import com.google.inject.spi.Dependency;
import com.google.inject.spi.ExposedBinding;
import com.google.inject.spi.HasDependencies;
import com.google.inject.spi.InstanceBinding;
import com.google.inject.spi.LinkedKeyBinding;
import com.google.inject.spi.ProviderBinding;
import com.google.inject.spi.ProviderInstanceBinding;
import com.google.inject.spi.ProviderKeyBinding;
import com.google.inject.spi.ProvidesMethodBinding;
import com.google.inject.spi.ProvidesMethodTargetVisitor;
import com.google.inject.spi.UntargettedBinding;
import upstart.util.collect.Optionals;
import upstart.util.collect.PersistentList;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

@Singleton
public class BindingResolver {
  private final LoadingCache<Key<?>, ResolvedBinding<?>> cache = CacheBuilder.newBuilder()
          .build(CacheLoader.from(this::load));
  private final Injector injector;

  @Inject
  private BindingResolver(Injector injector) {
    this.injector = injector;
  }

  @SuppressWarnings("unchecked")
  public <T> ResolvedBinding<T> resolve(Key<T> key) {
    return (ResolvedBinding<T>) cache.getUnchecked(key);
  }

  public <T> ResolvedBinding<T> resolve(Binding<T> binding) {
    return resolve(binding.getKey());
  }

  public boolean isSingleton(Key<?> key) {
    return Scopes.isSingleton(injector.getBinding(key));
  }

  private ResolvedBinding<?> load(Key<?> key) {
    return resolve(key, injector);
  }

  private static <T> ResolvedBinding<T> resolve(Key<T> key, Injector injector) {
    return new ResolvingBindingTargetVisitor<>(key, injector).traverseKey(key);
  }

  static class ResolvingBindingTargetVisitor<T> extends DefaultBindingTargetVisitor<Object, ResolvedBinding<T>> {
    private final Key<T> originalKey;
    private final Injector injector;

    ResolvingBindingTargetVisitor(Key<T> originalKey, Injector injector) {
      this.originalKey = originalKey;
      this.injector = injector;
    }

    @Override
    public ResolvedBinding<T> visit(ProviderKeyBinding<?> providerKeyBinding) {
      return traverseKey(providerKeyBinding.getProviderKey());
    }

    @Override
    public ResolvedBinding<T> visit(LinkedKeyBinding<?> linkedKeyBinding) {
      return traverseKey(linkedKeyBinding.getLinkedKey());
    }

    @Override
    public ResolvedBinding<T> visit(ExposedBinding<?> exposedBinding) {
      return resolve(originalKey, exposedBinding.getPrivateElements().getInjector());
    }

    @Override
    public ResolvedBinding<T> visit(ProviderBinding<?> providerBinding) {
      return traverseKey(providerBinding.getProvidedKey());
    }

    @Override
    protected ResolvedBinding<T> visitOther(Binding<?> binding) {
      return new ResolvedBinding<>(originalKey, binding, injector);
    }

    ResolvedBinding<T> traverseKey(Key<?> nextKey) {
      return traverse(injector.getBinding(nextKey));
    }

    ResolvedBinding<T> traverse(Binding<?> binding) {
      return binding.acceptTargetVisitor(this);
    }
  }

  /**
   * The "resolved" binding for the {@link #originalKey()}, which represents the strategy guice will apply to provide
   * an instance of {@link T}. Calculated by applying a {@link ResolvingBindingTargetVisitor} to the originalKey.
   * <p/>
   * One of:
   * <ul>
   *   <li>{@link UntargettedBinding}</li>
   *   <li>{@link ConstructorBinding}</li>
   *   <li>{@link InstanceBinding}</li>
   *   <li>{@link ProviderInstanceBinding}</li>
   *   <li>{@link ConvertedConstantBinding}</li>
   * </ul>
   */
  public static class ResolvedBinding<T> {

    private final Key<? extends T> originalKey;
    private final Binding<?> binding;
    private final Injector injector; // each binding is specific to its injector (important for private-bindings)

    ResolvedBinding(Key<? extends T> originalKey, Binding<?> binding, Injector injector) {
      this.originalKey = originalKey;
      this.binding = binding;
      this.injector = injector;
    }

    public Key<? extends T> originalKey() {
      return originalKey;
    }

    public Binding<?> binding() {
      return binding;
    }

    public boolean isConstructedByGuice() {
      return (binding instanceof UntargettedBinding) || (binding instanceof ConstructorBinding);
    }

    /**
     * The type of the object constructed by this binding. Note that this may be the type of a {@link Provider},
     * rather than {@link T}, if the {@link #originalKey()} was bound to a provider.
     * @return
     */
    public Class<?> rawBindingType() {
      return binding.getKey().getTypeLiteral().getRawType();
    }

    public T getInstance() {
      // note: don't use binding.getProvider().get(), it doesn't honor scoping rules (eg, SINGLETON)
      return injector.getInstance(originalKey);
    }

    Set<ResolvedBinding<?>> findNearestDependencies(GuiceDependencyGraph.ExternalDependencies externalDeps, Predicate<? super ResolvedBinding<?>> filter) {
      final Set<ResolvedBinding<?>> result = Sets.newHashSet();

      PersistentList<ResolvedBinding<?>> context = PersistentList.of(this);
      Set<ResolvedBinding<?>> uniqueBindings = new HashSet<>();
      traverseDependencies(context, externalDeps, input -> {
        if (!uniqueBindings.add(input)) return false;

        boolean matches = filter.test(input);
        if (matches) result.add(input);
        return !matches;
      });

      return result;
    }

    private void traverseDependencies(PersistentList<ResolvedBinding<?>> context, GuiceDependencyGraph.ExternalDependencies externalDeps, Predicate<? super ResolvedBinding<?>> visitor) {
      try {
        Streams.concat(getDirectDependencies(), externalDeps.resolvedDependencies(this).stream())
                .filter(visitor)
                .forEach(dependency -> dependency.traverseDependencies(context.with(dependency), externalDeps, visitor));
      } catch (BindingResolutionException e) {
        throw e;
      } catch (Exception e) {
        throw new BindingResolutionException(context, e);
      }
    }

    private Stream<ResolvedBinding<?>> getDirectDependencies() {
      return binding.acceptTargetVisitor(new DependencyResolvingVisitor(injector));
    }

    public String toString() {
      return binding.toString();
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      ResolvedBinding<?> that = (ResolvedBinding<?>) o;

      // use reference-equality: binding.equals() is incorrect for similar bindings from PrivateModules
      return binding == that.binding;
    }

    @Override
    public int hashCode() {
      return binding.hashCode();
    }

    private static class DependencyResolvingVisitor
            extends DefaultBindingTargetVisitor<Object, Stream<ResolvedBinding<?>>>
            implements MultibindingsTargetVisitor<Object, Stream<ResolvedBinding<?>>>,
            ProvidesMethodTargetVisitor<Object, Stream<ResolvedBinding<?>>>,
            AssistedInjectTargetVisitor<Object, Stream<ResolvedBinding<?>>>
    {
      private final Injector injector;

      private DependencyResolvingVisitor(Injector injector) {
        this.injector = injector;
      }

      @Override
      public Stream<ResolvedBinding<?>> visit(ConstructorBinding<?> constructorBinding) {
        return resolveDependencies(constructorBinding);
      }

      @Override
      public Stream<ResolvedBinding<?>> visit(ProviderInstanceBinding<?> providerInstanceBinding) {
        return resolveDependencies(providerInstanceBinding);
      }

      @Override
      public Stream<ResolvedBinding<?>> visit(InstanceBinding<?> instanceBinding) {
        return resolveDependencies(instanceBinding);
      }

      @Override
      public Stream<ResolvedBinding<?>> visit(MultibinderBinding<?> multibinding) {
        return resolveBindings(multibinding.getElements().stream());
      }

      @Override
      public Stream<ResolvedBinding<?>> visit(MapBinderBinding<?> mapbinding) {
        return resolveBindings(mapbinding.getEntries().stream().map(Map.Entry::getValue));
      }

      @Override public Stream<ResolvedBinding<?>> visit(OptionalBinderBinding<?> optionalBinding) {
        return resolveBindings(Stream.of(Optionals.or(
                Optional.ofNullable(optionalBinding.getActualBinding()),
                Optional.ofNullable(optionalBinding.getDefaultBinding())
        ).orElseThrow(() -> new IllegalStateException("OptionalBinding had no actual binding: " + optionalBinding))));
      }

      @Override
      public Stream<ResolvedBinding<?>> visit(AssistedInjectBinding<?> assistedInjectBinding) {
        // NOTE: we must assume that the consuming component will use ALL of the factory-methods from the
        // factory-interface it injected. To avoid dragging in superfluous dependencies, factory-interfaces should
        // generally only implement a single method.
        return assistedInjectBinding.getAssistedMethods().stream()
                .flatMap(method -> resolveDependencies(method.getDependencies().stream()));
      }

      @Override
      public Stream<ResolvedBinding<?>> visit(ProvidesMethodBinding<?> providesMethodBinding) {
        return resolveDependencies(providesMethodBinding);
      }

      @Override
      protected Stream<ResolvedBinding<?>> visitOther(Binding<?> binding) {
        return Stream.of();
      }

      private Stream<ResolvedBinding<?>> resolveDependencies(HasDependencies binding) {
        return resolveDependencies(binding.getDependencies().stream());
      }

      private Stream<ResolvedBinding<?>> resolveDependencies(Stream<Dependency<?>> dependencies) {
        return resolveBindings(dependencies.map(Dependency::getKey).map(injector::getBinding));
      }


      private Stream<ResolvedBinding<?>> resolveBindings(Stream<Binding<?>> elements) {
        return elements.map(binding -> new ResolvingBindingTargetVisitor<>(binding.getKey(), injector).traverse(binding));
      }
    }
  }

  static class BindingResolutionException extends RuntimeException {
    BindingResolutionException(PersistentList<ResolvedBinding<?>> context, Exception e) {
      super("Exception traversing dependencies for:\n  " + Joiner.on("\n  -> ").join(context.reversed()), e);
    }
  }
}
